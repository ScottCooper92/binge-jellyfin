package com.cooper.binge.companion.jellyfin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.JellyfinUnreadableAnswer
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.server.SignedIn
import com.cooper.binge.companion.jellyfin.server.authenticateByName
import com.cooper.binge.companion.jellyfin.server.authenticateWithQuickConnect
import com.cooper.binge.companion.jellyfin.server.logout
import com.cooper.binge.companion.jellyfin.server.me
import com.cooper.binge.companion.jellyfin.server.publicInfo
import com.cooper.binge.companion.jellyfin.server.quickConnectAuthenticated
import com.cooper.binge.companion.jellyfin.server.quickConnectEnabled
import com.cooper.binge.companion.jellyfin.server.quickConnectInitiate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import java.io.IOException

/** Why a step failed, as the screen words it. */
enum class SetupError { InvalidAddress, Unreachable, NotJellyfin, WrongCredentials, QuickConnectOff, QuickConnectExpired, Generic }

sealed interface UiState {
    data object Loading : UiState

    /** Step one: the server's address. */
    data class EnterServer(
        val address: String = "",
        val checking: Boolean = false,
        val error: SetupError? = null,
        /** The servers that answered on the local network, and whether the search is still running. */
        val discovered: List<DiscoveredServer> = emptyList(),
        val discovering: Boolean = false,
    ) : UiState

    /** Step two: sign in to a server that answered. */
    data class SignIn(
        val baseUrl: String,
        val server: PublicServerInfo,
        val username: String = "",
        val password: String = "",
        val busy: Boolean = false,
        val quickConnectCode: String? = null,
        val error: SetupError? = null,
    ) : UiState {
        val cleartext: Boolean get() = baseUrl.startsWith("http://")
    }

    data class SignedIn(
        val session: JellyfinSession,
        val titles: Int? = null,
        val refreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        val confirmingSignOut: Boolean = false,
        /** From the user's own policy, read with each refresh: what decides the console's admin pages. */
        val isAdministrator: Boolean = false,
    ) : UiState {
        val cleartext: Boolean get() = session.baseUrl.startsWith("http://")
    }
}

/** The app's one screen state machine: server, sign-in (password or Quick Connect), then the signed-in settings. */
class JellyfinViewModel(
    private val sessions: SessionStore,
    private val connection: Connection,
    private val index: LibraryIndex,
    /** The servers that answer a broadcast on the local network; empty when none does. */
    private val discover: suspend () -> List<DiscoveredServer>,
    private val quickConnectPollMillis: Long = QUICK_CONNECT_POLL_MILLIS,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var quickConnect: Job? = null

    init {
        viewModelScope.launch {
            val session = sessions.current()
            _state.value = if (session == null) UiState.EnterServer() else UiState.SignedIn(session)
            if (session != null) refresh()
        }
    }

    fun onAddress(value: String) = _state.update { (it as? UiState.EnterServer)?.copy(address = value, error = null) ?: it }

    fun onUsername(value: String) = _state.update { (it as? UiState.SignIn)?.copy(username = value, error = null) ?: it }

    fun onPassword(value: String) = _state.update { (it as? UiState.SignIn)?.copy(password = value, error = null) ?: it }

    /** Looks for servers on the local network once per visit to the server step. A server is one tap away; nothing found shows nothing. */
    fun discoverServers() {
        val current = _state.value as? UiState.EnterServer ?: return
        if (current.discovering) return
        _state.value = current.copy(discovering = true)
        viewModelScope.launch {
            val found = discover()
            _state.update { (it as? UiState.EnterServer)?.copy(discovered = found, discovering = false) ?: it }
        }
    }

    fun connect() {
        val current = _state.value as? UiState.EnterServer ?: return
        val url = JellyfinClient.parseBaseUrl(current.address)
        if (url == null) {
            _state.value = current.copy(error = SetupError.InvalidAddress)
            return
        }
        _state.value = current.copy(checking = true, error = null)
        viewModelScope.launch {
            val info =
                try {
                    connection.anonymous(url).publicInfo().takeIf { it.serverId.isNotBlank() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    Log.i(TAG, "The address answered, but not as Jellyfin", e)
                    null
                } catch (e: JellyfinUnreadableAnswer) {
                    Log.i(TAG, "The address answered something that is not Jellyfin", e)
                    null
                } catch (e: IOException) {
                    Log.i(TAG, "Server check failed", e)
                    _state.update { (it as? UiState.EnterServer)?.copy(checking = false, error = SetupError.Unreachable) ?: it }
                    return@launch
                }
            if (info == null) {
                _state.update { (it as? UiState.EnterServer)?.copy(checking = false, error = SetupError.NotJellyfin) ?: it }
            } else {
                _state.value = UiState.SignIn(baseUrl = info.baseUrl.toString().trimEnd('/'), server = info)
            }
        }
    }

    fun signInWithPassword() {
        val current = _state.value as? UiState.SignIn ?: return
        if (current.username.isBlank()) return
        _state.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val url = JellyfinClient.parseBaseUrl(current.baseUrl) ?: return@launch
            try {
                finish(url, current.server, connection.anonymous(url).authenticateByName(current.username.trim(), current.password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: JellyfinHttpException) {
                Log.i(TAG, "Password sign-in refused", e)
                val error = if (e.code == HTTP_UNAUTHORIZED) SetupError.WrongCredentials else SetupError.Generic
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, error = error) ?: it }
            } catch (e: JellyfinUnreadableAnswer) {
                Log.i(TAG, "Password sign-in answered something that is not Jellyfin", e)
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, error = SetupError.Generic) ?: it }
            } catch (e: IOException) {
                Log.i(TAG, "Password sign-in failed", e)
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, error = SetupError.Unreachable) ?: it }
            }
        }
    }

    /** Shows a code, then polls until the user approves it in a signed-in Jellyfin app, as Seerr's Plex PIN flow does. */
    fun startQuickConnect() {
        val current = _state.value as? UiState.SignIn ?: return
        quickConnect?.cancel()
        quickConnect =
            viewModelScope.launch {
                val url = JellyfinClient.parseBaseUrl(current.baseUrl) ?: return@launch
                try {
                    awaitQuickConnect(url, current.server)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    Log.i(TAG, "Quick Connect refused", e)
                    _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, error = SetupError.QuickConnectExpired) ?: it }
                } catch (e: JellyfinUnreadableAnswer) {
                    Log.i(TAG, "Quick Connect answered something that is not Jellyfin", e)
                    _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, error = SetupError.Generic) ?: it }
                } catch (e: IOException) {
                    Log.i(TAG, "Quick Connect failed", e)
                    _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, error = SetupError.Unreachable) ?: it }
                }
            }
    }

    private suspend fun awaitQuickConnect(
        url: HttpUrl,
        server: PublicServerInfo,
    ) {
        val client = connection.anonymous(url)
        if (!client.quickConnectEnabled()) {
            _state.update { (it as? UiState.SignIn)?.copy(error = SetupError.QuickConnectOff) ?: it }
            return
        }
        val start = client.quickConnectInitiate()
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = start.code, error = null) ?: it }
        val deadline = System.currentTimeMillis() + QUICK_CONNECT_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            delay(quickConnectPollMillis)
            if (client.quickConnectAuthenticated(start.secret)) {
                finish(url, server, client.authenticateWithQuickConnect(start.secret))
                return
            }
        }
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, error = SetupError.QuickConnectExpired) ?: it }
    }

    fun cancelQuickConnect() {
        quickConnect?.cancel()
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null) ?: it }
    }

    fun changeServer() {
        quickConnect?.cancel()
        val address = (_state.value as? UiState.SignIn)?.baseUrl.orEmpty()
        _state.value = UiState.EnterServer(address = address)
    }

    fun refresh() {
        val current = _state.value as? UiState.SignedIn ?: return
        _state.value = current.copy(refreshing = true, refreshFailed = false)
        viewModelScope.launch {
            val failed =
                try {
                    val signedIn = connection.signedIn()
                    if (signedIn == null) {
                        _state.update { (it as? UiState.SignedIn)?.copy(refreshing = false) ?: it }
                        return@launch
                    }
                    index.refresh(signedIn.second, signedIn.first.userId)
                    val admin = signedIn.second.me().isAdministrator
                    _state.update { (it as? UiState.SignedIn)?.copy(isAdministrator = admin) ?: it }
                    false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    Log.i(TAG, "Library refresh refused", e)
                    if (e.code == HTTP_UNAUTHORIZED) {
                        sessionRefused(current.session)
                        return@launch
                    }
                    true
                } catch (e: JellyfinUnreadableAnswer) {
                    Log.i(TAG, "Library refresh answered something that is not Jellyfin", e)
                    true
                } catch (e: IOException) {
                    Log.i(TAG, "Library refresh failed", e)
                    true
                }
            _state.update { (it as? UiState.SignedIn)?.copy(refreshing = false, refreshFailed = failed, titles = index.size) ?: it }
        }
    }

    /** The server no longer accepts the token (a revoked device, say): forget it and ask for a sign-in, as the Service's UNAUTHENTICATED does. */
    private suspend fun sessionRefused(session: JellyfinSession) {
        sessions.clear()
        index.clear()
        _state.value = UiState.EnterServer(address = session.baseUrl)
    }

    /** Shows or hides the sign-out confirm. */
    fun confirmSignOut(showing: Boolean) = _state.update { (it as? UiState.SignedIn)?.copy(confirmingSignOut = showing) ?: it }

    /** Ends the session on the server too, so the device leaves the user's Jellyfin devices list. A failure there still signs out here. */
    fun signOut() {
        _state.update { (it as? UiState.SignedIn)?.copy(confirmingSignOut = false) ?: it }
        viewModelScope.launch {
            val signedIn = connection.signedIn()
            attempt { signedIn?.second?.logout() }
            sessions.clear()
            index.clear()
            _state.value = UiState.EnterServer(address = signedIn?.first?.baseUrl.orEmpty())
        }
    }

    private suspend fun finish(
        url: HttpUrl,
        server: PublicServerInfo,
        signedIn: SignedIn,
    ) {
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
            _state.update { (it as? UiState.SignIn)?.copy(busy = false, quickConnectCode = null, error = SetupError.Generic) ?: it }
            return
        }
        index.clear()
        _state.value = UiState.SignedIn(session)
        refresh()
    }

    private companion object {
        const val TAG = "JellyfinSetup"
        const val HTTP_UNAUTHORIZED = 401
        const val QUICK_CONNECT_POLL_MILLIS = 3_000L
        const val QUICK_CONNECT_WINDOW_MILLIS = 10 * 60_000L
    }
}
