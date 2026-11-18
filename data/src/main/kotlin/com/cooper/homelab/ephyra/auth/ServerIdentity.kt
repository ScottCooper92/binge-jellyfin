package com.cooper.homelab.ephyra.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.connection.DeviceIdentity
import java.util.UUID

/**
 * This install's [ServerId] for the one server the app holds: minted on first use and kept, whether the saved session lives in the
 * old file or in the shared library's per-server stores. Anything keyed by a server (a notification cursor, say) uses this id, and
 * [LegacySessionImport] and [RegistrySessionStore] save the server under it, so nothing is re-keyed when the switch flips.
 *
 * The app has one slot, so the id does not change when the user signs in to a different server. What was keyed to the old one is
 * cleared with the other server-scoped data when the server changes, as it is today.
 */
class ServerIdentity(
    dataStore: DataStore<Preferences>,
) {
    private val identity = DeviceIdentity(dataStore, KEY) { UUID.randomUUID().toString() }

    suspend fun currentServerId(): ServerId = ServerId(identity.id())

    private companion object {
        const val KEY = "current_server_id"
    }
}
