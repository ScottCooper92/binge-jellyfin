package com.cooper.binge.companion.jellyfin.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.binge.companion.sdk.attempt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** The signed-in session: one server, one user, and the access token Jellyfin minted for this device. */
data class JellyfinSession(
    val baseUrl: String,
    val serverName: String,
    val serverId: String,
    val serverVersion: String,
    val userId: String,
    val userName: String,
    val token: String,
)

/**
 * The saved session. Everything but the token is stored in the clear; the token goes through
 * [SecretCipher]. A session is only reported when the token decrypts, so a lost Keystore key reads
 * as signed out rather than as a session that fails every call.
 */
class SessionStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    /**
     * Where the Keystore work runs. Each encrypt or decrypt is a binder call to keystore2, and the
     * first sign-in also generates the key, which can be slow (StrongBox in particular), so none of it
     * runs on the caller's thread: the setup screens call from the main thread.
     */
    private val io: CoroutineDispatcher,
) {
    val session: Flow<JellyfinSession?> =
        dataStore.data
            .map { prefs ->
                val baseUrl = prefs[Keys.BASE_URL]
                val userId = prefs[Keys.USER_ID]
                val token = prefs[Keys.TOKEN]?.takeIf { it.isNotBlank() }?.let { cipher.decrypt(it) }
                if (baseUrl.isNullOrBlank() || userId.isNullOrBlank() || token == null) {
                    null
                } else {
                    JellyfinSession(
                        baseUrl = baseUrl,
                        serverName = prefs[Keys.SERVER_NAME].orEmpty(),
                        serverId = prefs[Keys.SERVER_ID].orEmpty(),
                        serverVersion = prefs[Keys.SERVER_VERSION].orEmpty(),
                        userId = userId,
                        userName = prefs[Keys.USER_NAME].orEmpty(),
                        token = token,
                    )
                }
            }.flowOn(io)
            .distinctUntilChanged()

    suspend fun current(): JellyfinSession? = session.first()

    /** Encrypts and saves [session]; false, saving nothing, when the Keystore refuses. */
    suspend fun save(session: JellyfinSession): Boolean {
        val token = attempt { withContext(io) { cipher.encrypt(session.token) } }.getOrElse { return false }
        dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = session.baseUrl
            prefs[Keys.SERVER_NAME] = session.serverName
            prefs[Keys.SERVER_ID] = session.serverId
            prefs[Keys.SERVER_VERSION] = session.serverVersion
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.USER_NAME] = session.userName
            prefs[Keys.TOKEN] = token
        }
        return true
    }

    /** Forgets the session. The device id stays: it is this install's identity to every server. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            val deviceId = prefs[Keys.DEVICE_ID]
            prefs.clear()
            deviceId?.let { prefs[Keys.DEVICE_ID] = it }
        }
    }

    /** This install's device id, minted on first use. Jellyfin keys sessions and Quick Connect by it. */
    suspend fun deviceId(): String {
        dataStore.data.first()[Keys.DEVICE_ID]?.let { return it }
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[Keys.DEVICE_ID] ?: UUID.randomUUID().toString().also { prefs[Keys.DEVICE_ID] = it }
        }
        return id
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val SERVER_ID = stringPreferencesKey("server_id")
        val SERVER_VERSION = stringPreferencesKey("server_version")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val TOKEN = stringPreferencesKey("token")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
