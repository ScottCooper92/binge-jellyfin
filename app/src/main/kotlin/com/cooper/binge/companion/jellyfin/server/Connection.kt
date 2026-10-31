package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Makes [JellyfinClient]s: an anonymous one for a typed address, and one for the saved session. */
class Connection(
    private val http: OkHttpClient,
    private val sessions: SessionStore,
    private val client: String,
    private val device: String,
    private val version: String,
    private val io: CoroutineDispatcher,
) {
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
