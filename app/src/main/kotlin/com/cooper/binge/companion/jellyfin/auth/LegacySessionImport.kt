package com.cooper.binge.companion.jellyfin.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.info
import com.cooper.homelab.core.foundation.warn
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.ConnectionSecurity
import com.cooper.homelab.core.server.connection.DeviceIdentity
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.flow.first
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/**
 * The one-time move of the saved session from the file the released versions wrote ([legacy]) into the registry and the login
 * store. The old file is only read and stays for one release, so a rollback still works. The token is copied as the blob it is, so
 * nothing is decrypted: one that no longer decrypts imports as a saved server that is signed out. The device id moves first, for a
 * user with a session or without, so Jellyfin sees the same device and leaves no stale one behind. [marker] makes the session part
 * run once, so a user who signs out is not signed back in from the old file. The old keys are written here and nowhere else.
 */
class LegacySessionImport(
    private val legacy: DataStore<Preferences>,
    private val registry: ServerRegistry,
    private val logins: LoginStore<JellyfinLogin>,
    private val device: DeviceIdentity,
    private val marker: DataStore<Preferences>,
    private val serverIdentity: ServerIdentity,
    private val logger: Logger,
) {
    suspend fun importOnce() {
        val old = legacy.data.first()
        old[DEVICE_ID]?.let { device.adopt(it) }
        if (marker.data.first()[DONE] == true) return
        if (registry.current().servers.isEmpty()) importSession(old)
        marker.edit { it[DONE] = true }
    }

    private suspend fun importSession(old: Preferences) {
        val url = old[BASE_URL]?.takeIf { it.isNotBlank() } ?: return
        val userId = old[USER_ID]?.takeIf { it.isNotBlank() }
        val address = ServerAddress.parse(url)
        if (address == null) {
            logger.warn(TAG, "legacy-address-unreadable")
            return
        }
        val id = serverIdentity.currentServerId()
        val token = old[TOKEN]?.takeIf { it.isNotBlank() }
        if (userId != null && token != null) {
            val fields =
                mapOf(
                    JellyfinLoginCodec.SERVER_ID to old[SERVER_ID].orEmpty(),
                    JellyfinLoginCodec.SERVER_VERSION to old[SERVER_VERSION].orEmpty(),
                    JellyfinLoginCodec.USER_ID to userId,
                    JellyfinLoginCodec.USER_NAME to old[USER_NAME].orEmpty(),
                )
            logins.importEncrypted(id, fields, token)
        }
        val name = old[SERVER_NAME]?.takeIf { it.isNotBlank() } ?: address.host
        registry.restore(SavedServer(id, address, name, JELLYFIN_KIND, ConnectionSecurity()))
        logger.info(TAG, "legacy-session-imported", listOf(LogField.Server(id), LogField.Flag("signedIn", userId != null && token != null)))
    }

    private companion object {
        const val TAG = "LegacySessionImport"
        val BASE_URL = stringPreferencesKey("base_url")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_VERSION = stringPreferencesKey("server_version")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val TOKEN = stringPreferencesKey("token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DONE = booleanPreferencesKey("legacy_import_done")
    }
}
