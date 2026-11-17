package com.cooper.binge.companion.jellyfin.auth

import kotlinx.coroutines.flow.Flow

/**
 * The signed-in session, which is one server and one user. [LegacySessionStore] keeps it in the file the released versions wrote.
 * [RegistrySessionStore] keeps it in the shared library's per-server stores behind a debug switch, and imports the old file once.
 */
interface SessionStore {
    /** The saved session, or null when signed out or its token does not decrypt. */
    val session: Flow<JellyfinSession?>

    suspend fun current(): JellyfinSession?

    /** Saves [session]; false, saving nothing, when it could not be saved. */
    suspend fun save(session: JellyfinSession): Boolean

    /** Forgets the session. The device id stays: it is this install's identity to every server. */
    suspend fun clear()

    /** This install's device id, minted on first use. Jellyfin keys sessions and Quick Connect by it. */
    suspend fun deviceId(): String
}
