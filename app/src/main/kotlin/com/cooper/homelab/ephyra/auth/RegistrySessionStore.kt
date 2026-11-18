package com.cooper.homelab.ephyra.auth

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.DeviceIdentity
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/** The word this app gives the saved server's `kind`. The library reads nothing into it. */
internal const val JELLYFIN_KIND = "jellyfin"

/**
 * The saved session in the shared library's per-server stores, as one server and one user: the active server of the registry and
 * its login. This app has no switcher, so [save] replaces that server and [clear] removes it.
 *
 * The first read, and every write, runs [importer] once, so a user who updates has the session the released version kept, and the
 * device id has moved with it. The token is decrypted on [io], as [LegacySessionStore] does: the setup screens collect on the main
 * thread, and a Keystore decrypt is a binder call.
 */
class RegistrySessionStore(
    private val registry: ServerRegistry,
    private val logins: LoginStore<JellyfinLogin>,
    private val importer: LegacySessionImport,
    private val serverIdentity: ServerIdentity,
    private val device: DeviceIdentity,
    private val io: CoroutineDispatcher,
) : SessionStore {
    private val importing = Mutex()
    private var imported = false

    override val session: Flow<JellyfinSession?> =
        registry.state
            .onStart { ready() }
            .flatMapLatest { state ->
                val server = state.activeServer
                if (server == null) {
                    flowOf(null)
                } else {
                    logins.observe(server.id).map { login -> login?.let { sessionOf(server, it) } }
                }
            }.flowOn(io)
            .distinctUntilChanged()

    override suspend fun current(): JellyfinSession? = session.first()

    override suspend fun save(session: JellyfinSession): Boolean {
        ready()
        val address = ServerAddress.parse(session.baseUrl) ?: return false
        val name = session.serverName.ifBlank { address.host }
        val existing = registry.current().activeServer
        val id = existing?.id ?: serverIdentity.currentServerId()
        // The login goes first. It encrypts before it writes, so a keymaster that throws leaves the registry exactly as it was.
        val login = JellyfinLogin(session.serverId, session.serverVersion, session.userId, session.userName, session.token)
        if (attempt { logins.save(id, login) }.isFailure) return false
        if (existing != null) {
            registry.update(existing.copy(address = address, name = name))
        } else {
            registry.restore(SavedServer(id, address, name, JELLYFIN_KIND))
        }
        return true
    }

    override suspend fun clear() {
        ready()
        registry.current().servers.forEach { server ->
            registry.remove(server.id)
            logins.remove(server.id)
        }
    }

    override suspend fun deviceId(): String {
        ready()
        return device.id()
    }

    /** Runs the one-time import before anything reads or writes, and never twice. */
    private suspend fun ready() {
        if (imported) return
        importing.withLock {
            if (!imported) {
                importer.importOnce()
                imported = true
            }
        }
    }

    private fun sessionOf(
        server: SavedServer,
        login: JellyfinLogin,
    ) = JellyfinSession(
        // The app keeps its base URL without the trailing slash the library's canonical form ends in.
        baseUrl = server.address.url.trimEnd('/'),
        serverName = server.name,
        serverId = login.serverId,
        serverVersion = login.serverVersion,
        userId = login.userId,
        userName = login.userName,
        token = login.token,
    )
}
