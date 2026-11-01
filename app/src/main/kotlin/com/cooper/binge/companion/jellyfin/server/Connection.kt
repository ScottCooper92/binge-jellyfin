package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/** Makes [JellyfinClient]s: an anonymous one for a typed address, and one for the saved session. */
class Connection(
    private val http: OkHttpClient,
    private val sessions: SessionStore,
    private val client: String,
    private val device: String,
    private val version: String,
    private val io: CoroutineDispatcher,
) {
    private val refusals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The saved session's token stopped working: the server refused it outright (revoked, the device
     * removed, the password changed elsewhere). The home answers by sending the user to sign in.
     */
    val refused: SharedFlow<Unit> = refusals.asSharedFlow()

    fun reportRefused() {
        refusals.tryEmit(Unit)
    }

    /** Lives as long as the connection, so a save outlives the page that started it. */
    private val saveScope = CoroutineScope(SupervisorJob() + io)
    private val pendingSaves: MutableSet<Job> = ConcurrentHashMap.newKeySet()

    init {
        // A save is built from one account's configuration, so it dies with the session it was made under.
        saveScope.launch {
            sessions.session
                .map { session -> session?.let { Triple(it.baseUrl, it.userId, it.token) } }
                .distinctUntilChanged()
                .drop(1)
                .collect { pendingSaves.toList().forEach { it.cancel() } }
        }
    }

    /**
     * Runs [block], a save the user made, to its end even if the page that made it closes first.
     * Settings save as made, so closing a page must not drop a change still on its way.
     */
    fun launchSave(block: suspend () -> Unit) {
        val job = saveScope.launch(start = CoroutineStart.LAZY) { block() }
        pendingSaves += job
        job.invokeOnCompletion { pendingSaves -= job }
        job.start()
    }

    /** Waits for every save started so far, so a page opened next reads what the last one saved. */
    suspend fun awaitSaves() = pendingSaves.toList().joinAll()

    suspend fun identity(): ClientIdentity = ClientIdentity(client, device, sessions.deviceId(), version)

    suspend fun anonymous(baseUrl: HttpUrl): JellyfinClient = JellyfinClient(http, baseUrl, identity(), io)

    /** The saved session and a client signed in as it, or null when signed out. */
    suspend fun signedIn(): Pair<JellyfinSession, JellyfinClient>? {
        val session = sessions.current() ?: return null
        return session to JellyfinClient(http, session.baseUrl.toHttpUrl(), identity(), io, session.token)
    }

    /** The signed-in client, again each time the saved session changes; null while signed out. */
    fun clients(): Flow<JellyfinClient?> =
        sessions.session.map { session ->
            session?.let { JellyfinClient(http, it.baseUrl.toHttpUrl(), identity(), io, it.token) }
        }
}
