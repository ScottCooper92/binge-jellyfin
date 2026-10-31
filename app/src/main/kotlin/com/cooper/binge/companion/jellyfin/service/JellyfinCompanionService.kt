package com.cooper.binge.companion.jellyfin.service

import android.content.Context
import android.os.Process
import com.binge.companion.sdk.BingeHosts
import com.binge.companion.sdk.HostPolicy
import com.binge.companion.sdk.IntegrationService
import com.cooper.binge.companion.jellyfin.BuildConfig
import com.cooper.binge.companion.jellyfin.JellyfinApp
import com.cooper.binge.companion.jellyfin.R
import io.grpc.BindableService
import io.grpc.Status
import io.grpc.binder.SecurityPolicy
import java.time.Instant
import java.util.Locale

/** The exported Service Binge binds to: LIBRARY v1, admitted for Binge's certificate on release and its package names on debug. */
class JellyfinCompanionService : IntegrationService() {
    override fun services(): List<BindableService> {
        val graph = (application as JellyfinApp).graph
        val messages = AndroidUserMessages(this)
        return listOf(
            JellyfinLibraryService(
                reads = LibraryReads(graph.connection, graph.index, messages),
                playback = PlaybackSources(now = Instant::now),
                versionName = BuildConfig.VERSION_NAME,
                messages = messages,
            ),
        )
    }

    override fun hostPolicy(): SecurityPolicy =
        selectHostPolicy(
            admitsUnverifiedCallers = ADMITS_UNVERIFIED_CALLERS,
            pinned = { HostPolicy.pinned(this, listOf(BingeHosts.release)) },
            debug = { SelfOrPolicy(selfUid = Process.myUid(), policy = HostPolicy.anyCertificateOf(this, tag = TAG)) },
        )

    private companion object {
        const val TAG = "JellyfinCompanion"
    }
}

/** True only on a debug build, read from [BuildConfig.DEBUG] and never from anything a user can switch. */
internal val ADMITS_UNVERIFIED_CALLERS: Boolean = BuildConfig.DEBUG

/**
 * Release pins Binge's published certificate (`BingeHosts.release`) and refuses any other signer. Debug
 * admits Binge's package names under any certificate, since a debug Binge is signed with its developer's
 * own key, plus this app's own uid.
 */
internal fun selectHostPolicy(
    admitsUnverifiedCallers: Boolean,
    pinned: () -> SecurityPolicy,
    debug: () -> SecurityPolicy,
): SecurityPolicy = if (admitsUnverifiedCallers) debug() else pinned()

/** [policy], plus this app's own uid. That uid is this app, so no other app gets in by it. */
internal class SelfOrPolicy(
    private val selfUid: Int,
    private val policy: SecurityPolicy,
) : SecurityPolicy() {
    override fun checkAuthorization(uid: Int): Status = if (uid == selfUid) Status.OK else policy.checkAuthorization(uid)
}

class AndroidUserMessages(
    private val context: Context,
) : UserMessages {
    override fun signedOut(): String = context.getString(R.string.error_signed_out)

    override fun unreachable(): String = context.getString(R.string.error_unreachable)

    override fun locale(): String =
        context.resources.configuration.locales
            .get(0)
            ?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
}
