package com.cooper.binge.companion.jellyfin.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.binge.companion.kit.SecretCipher
import com.cooper.binge.companion.jellyfin.PlainCipher
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.server.connection.DeviceIdentity
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import java.io.File
import java.util.UUID
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/**
 * All the files of one install in a temp folder: the released store over `session`, and the per-server stores over their own.
 * A second [Phone] over the same folder, after [stop] on the first, is the app starting again.
 */
class Phone(
    private val folder: File,
    val cipher: SecretCipher = PlainCipher,
    io: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Unconfined,
) {
    val warnings = mutableListOf<String>()
    private val logger = Logger { level, _, event, _, _ -> if (level == LogLevel.Warn) warnings += event }
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    suspend fun stop() = job.cancelAndJoin()

    private fun file(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(folder, "$name.preferences_pb")
        }

    val legacyFile = file("session")
    val registryFile = file("servers")
    val loginsFile = file("server_logins")
    val identityFile = file("server_identity")
    val released = LegacySessionStore(legacyFile, cipher, io)
    val serverIdentity = ServerIdentity(identityFile)
    val registry = ServerRegistry(registryFile, "registry", { UUID.randomUUID().toString() }, logger)
    val logins = LoginStore(loginsFile, JellyfinLoginCodec, cipher.forLibrary(), logger)
    val device = DeviceIdentity(registryFile, "device_id") { UUID.randomUUID().toString() }
    val importer = LegacySessionImport(legacyFile, registry, logins, device, registryFile, serverIdentity, logger)
    val store = RegistrySessionStore(registry, logins, importer, serverIdentity, device, io)
}
