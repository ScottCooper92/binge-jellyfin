package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import okhttp3.HttpUrl

/**
 * Where a sign-in ends: the session is kept and the library index of the last account is dropped. It is what the shared flow's
 * session sink calls, and the first-run wizard ends in it too, so every way of signing in ends in the one place. What counts it is
 * the caller's.
 */
internal class SessionHandoff(
    private val sessions: SessionStore,
    private val index: LibraryIndex,
) {
    /**
     * Keeps the session that [signedIn] opened on the server at [url], and returns the signed-in state it begins. The store may
     * refuse it: then nothing changes, so null is the caller's to show and to count.
     */
    suspend fun save(
        url: HttpUrl,
        server: PublicServerInfo,
        signedIn: SignedIn,
    ): UiState.SignedIn? {
        val session =
            JellyfinSession(
                baseUrl = url.toString().trimEnd('/'),
                serverName = server.serverName,
                serverId = signedIn.serverId.ifBlank { server.serverId },
                serverVersion = server.version,
                userId = signedIn.userId,
                userName = signedIn.userName,
                token = signedIn.token,
            )
        if (!sessions.save(session)) return null
        index.clear()
        return signedInState(session)
    }
}
