package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.AddressPolicy
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.telemetry.SignInReports
import okhttp3.HttpUrl

/**
 * Where a sign-in ends: the session is kept, the library index of the last account is dropped, and the sign-in is counted. It is
 * what the shared flow's session sink will be, so every way of signing in (password, Quick Connect, the demo, the first-run
 * wizard) ends in the one place.
 */
internal class SessionHandoff(
    private val sessions: SessionStore,
    private val index: LibraryIndex,
    private val reports: SignInReports,
    private val addressPolicy: AddressPolicy,
) {
    /**
     * Keeps the session that [signedIn] opened on the server at [url], and returns the signed-in state it begins. The store may
     * refuse it: then the failure is counted under [method] and nothing else changes, so null is the caller's to show.
     */
    suspend fun save(
        url: HttpUrl,
        server: PublicServerInfo,
        signedIn: SignedIn,
        method: String,
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
        if (!sessions.save(session)) {
            reports.failed(method, SetupError.Generic)
            return null
        }
        index.clear()
        reports.signedIn(method)
        return signedInState(addressPolicy, session)
    }
}
