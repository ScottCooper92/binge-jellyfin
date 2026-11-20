package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.FieldKind
import com.cooper.homelab.core.presentation.connect.FieldSpec
import com.cooper.homelab.core.presentation.connect.Link
import com.cooper.homelab.core.presentation.connect.LinkExpiredException
import com.cooper.homelab.core.presentation.connect.LinkNotOfferedException
import com.cooper.homelab.core.presentation.connect.LinkProvider
import com.cooper.homelab.core.presentation.connect.LinkStash
import com.cooper.homelab.core.presentation.connect.NotThisServerException
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.ServerProbe
import com.cooper.homelab.core.presentation.connect.SignInMode
import com.cooper.homelab.core.presentation.connect.awaitApproval
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.network.CleartextRefusedException
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.server.authenticateByName
import com.cooper.homelab.ephyra.server.authenticateWithQuickConnect
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.server.publicInfo
import com.cooper.homelab.ephyra.server.quickConnectAuthenticated
import com.cooper.homelab.ephyra.server.quickConnectEnabled
import com.cooper.homelab.ephyra.server.quickConnectEnabledOrNull
import com.cooper.homelab.ephyra.server.quickConnectInitiate
import com.cooper.homelab.ephyra.server.serverFailure
import com.cooper.homelab.ephyra.telemetry.AnalyticsEvents
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private const val TAG = "JellyfinSetup"

/** The fields of the password sign-in, by the ids the form's values are keyed by. */
internal const val USERNAME_FIELD = "username"
internal const val PASSWORD_FIELD = "password"

/**
 * The ways this app signs in to a Jellyfin server. The ids are the names the sign-in is counted under. The password may be empty (an
 * account with none), so only the name is required.
 */
internal enum class SetupMode(
    override val id: String,
    override val fields: List<FieldSpec>,
) : SignInMode {
    Password(
        AnalyticsEvents.METHOD_PASSWORD,
        listOf(FieldSpec(USERNAME_FIELD, FieldKind.Username), FieldSpec(PASSWORD_FIELD, FieldKind.Secret, required = false)),
    ),
    QuickConnect(AnalyticsEvents.METHOD_QUICK_CONNECT, emptyList()),
}

/** An address answered, but only after asking for a client certificate this app cannot send. */
internal class ClientCertificateRequired(
    cause: IOException,
) : IOException("the server asks for a client certificate", cause)

/**
 * Reads a server without signing in: who it answers as, whether its setup is done and whether Quick Connect is on. Each address the
 * machine tries is asked once. An address that answers but not as Jellyfin is [NotThisServerException]; one that does not answer is the
 * failure it gave, with a request for a client certificate told apart from silence, since the user can act on the first.
 */
internal class JellyfinProbe(
    private val connection: Connection,
) : ServerProbe<PublicServerInfo> {
    override suspend fun probe(address: ServerAddress): ProbedServer<PublicServerInfo> {
        val url = address.url.toHttpUrlOrNull() ?: throw NotThisServerException()
        val info = read(url) ?: throw NotThisServerException()
        // A server that is not set up has no account, so Quick Connect is not asked about. A read that fails or is unclear keeps it on offer.
        val quickConnect =
            info.setupIncomplete ||
                runRead { connection.anonymous(info.baseUrl).quickConnectEnabledOrNull() } != false
        return ProbedServer(
            address = ServerAddress.parse(info.baseUrl.toString()) ?: address,
            name = info.serverName,
            setUp = !info.setupIncomplete,
            modes = if (quickConnect) listOf(SetupMode.Password, SetupMode.QuickConnect) else listOf(SetupMode.Password),
            preview = info,
        )
    }

    /** The server's own answer, or null when the address answered but not as Jellyfin. A failure to answer is thrown. */
    private suspend fun read(url: okhttp3.HttpUrl): PublicServerInfo? {
        val attempts = mutableListOf<IOException>()
        return try {
            connection
                .anonymous(url) {
                    connection.logger.noted(TAG, "an attempt failed", it)
                    synchronized(attempts) { attempts += it }
                }.publicInfo()
                .takeIf { it.serverId.isNotBlank() }
        } catch (e: IOException) {
            notAnswered(e, synchronized(attempts) { attempts.toList() })
        }
    }

    /** What a failed read means: null for an address that answered, not as Jellyfin, and otherwise the failure itself, thrown. */
    private fun notAnswered(
        failure: IOException,
        attempts: List<IOException>,
    ): PublicServerInfo? {
        connection.logger.noted(TAG, "the address did not answer as a media server", failure)
        if (failure is JellyfinHttpException || failure is JellyfinUnreadableAnswer) return null
        val needsCertificate = failure !is CleartextRefusedException && asksForClientCertificate(attempts + failure)
        throw if (needsCertificate) ClientCertificateRequired(failure) else failure
    }

    private suspend fun <T> runRead(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            connection.logger.noted(TAG, "whether Quick Connect is on could not be read", e)
            null
        }
}

/** Why a setup call failed, in the shared library's terms: what [SetupError] is worded from. */
internal suspend fun Throwable.setupFailure(): ServerFailure =
    when (this) {
        is ClientCertificateRequired -> ServerFailure.ClientCertificateNeeded
        is CleartextRefusedException -> ServerFailure.CleartextBlocked
        is JellyfinUnreadableAnswer -> ServerFailure.Unreadable
        else -> serverFailure()
    }

/** Quick Connect as a link sign-in: the server gives a code, the user approves it in a signed-in Jellyfin app, and this polls until they do. */
internal class JellyfinQuickConnect(
    private val connection: Connection,
    private val pollMillis: Long,
    private val window: Duration = 10.minutes,
) : LinkProvider<PublicServerInfo, SignedIn> {
    override suspend fun start(
        server: ProbedServer<PublicServerInfo>,
        forTelevision: Boolean,
    ): Link =
        refusedAsExpired {
            val client = connection.anonymous(server.preview.baseUrl)
            if (!client.quickConnectEnabled()) throw LinkNotOfferedException()
            val started = client.quickConnectInitiate()
            Link(code = started.code, resumeWith = started.secret)
        }

    override suspend fun await(
        server: ProbedServer<PublicServerInfo>,
        link: Link,
    ): SignedIn =
        refusedAsExpired {
            val client = connection.anonymous(server.preview.baseUrl)
            val secret = link.resumeWith ?: throw LinkExpiredException()
            awaitApproval(pollMillis.milliseconds, window) {
                if (client.quickConnectAuthenticated(secret)) client.authenticateWithQuickConnect(secret) else null
            }
        }

    override suspend fun resume(
        server: ProbedServer<PublicServerInfo>,
        code: String,
        resumeWith: String,
    ): Link = Link(code = code, resumeWith = resumeWith)

    /** A server that refuses the call, whatever it says, ends the wait the way an expired code does. */
    private suspend fun <T> refusedAsExpired(block: suspend () -> T): T =
        try {
            block()
        } catch (e: JellyfinHttpException) {
            connection.logger.noted(TAG, "Quick Connect refused", e)
            throw LinkExpiredException()
        }
}

/** A link stash that remembers nothing, for a view model built without one: the app's is [SharedPreferencesLinkStash], and a test's is its own. */
internal object NoLinkStash : LinkStash {
    override fun read(): String? = null

    override fun write(value: String) = Unit

    override fun clear() = Unit
}

/** The demo's sign-in did not end in a saved session. */
internal class DemoSignInFailed : Exception("the demo's session could not be kept")

/** Signs in to the server at [url] with a username and password: what the password authenticator of the connect flow calls. */
internal suspend fun Connection.passwordSignIn(
    url: okhttp3.HttpUrl,
    username: String,
    password: String,
): SignedIn = anonymous(url).authenticateByName(username.trim(), password)
