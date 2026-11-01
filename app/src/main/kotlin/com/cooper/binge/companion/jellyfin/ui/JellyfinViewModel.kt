package com.cooper.binge.companion.jellyfin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
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
        /** The password sign-in's failure. It belongs to the password form, and only a wrong password marks the field. */
        val passwordError: SetupError? = null,
        /** Quick Connect's failure: it couldn't get or keep a code. It belongs to Quick Connect, never the password form. */
        val quickConnectError: SetupError? = null,
        /** A Quick Connect code is being asked for, so a retry shows progress rather than the last failure. */
        val gettingCode: Boolean = false,
    ) : UiState {
        val cleartext: Boolean get() = baseUrl.startsWith("http://")
    }

    data class SignedIn(
        val session: JellyfinSession,
        val titles: Int? = null,
        val refreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        val confirmingSignOut: Boolean = false,
        /** What the server is and has turned on: the version gate. Read again with each refresh. */
        val profile: JellyfinServerProfile = JellyfinServerProfile.from(session.serverVersion),
        /** What the signed-in user may do, from their own policy: the account gate. Read with each refresh. */
        val account: JellyfinAccount = JellyfinAccount(session.userName),
    ) : UiState {
        val cleartext: Boolean get() = session.baseUrl.startsWith("http://")
        val isAdministrator: Boolean get() = account.isAdministrator
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
        // A console page found the token refused: sign-in again, as a refused library refresh does.
        viewModelScope.launch {
            connection.refused.collect { (_state.value as? UiState.SignedIn)?.let { sessionRefused(it.session) } }
        }
    }

    fun onAddress(value: String) = _state.update { (it as? UiState.EnterServer)?.copy(address = value, error = null) ?: it }

    fun onUsername(value: String) = _state.update { (it as? UiState.SignIn)?.copy(username = value, passwordError = null) ?: it }

    fun onPassword(value: String) = _state.update { (it as? UiState.SignIn)?.copy(password = value, passwordError = null) ?: it }

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
        _state.value = current.copy(busy = true, passwordError = null)
        viewModelScope.launch {
            val url = JellyfinClient.parseBaseUrl(current.baseUrl) ?: return@launch
            try {
                finish(url, current.server, connection.anonymous(url).authenticateByName(current.username.trim(), current.password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: JellyfinHttpException) {
                Log.i(TAG, "Password sign-in refused", e)
                val error = if (e.code == HTTP_UNAUTHORIZED) SetupError.WrongCredentials else SetupError.Generic
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, passwordError = error) ?: it }
            } catch (e: JellyfinUnreadableAnswer) {
                Log.i(TAG, "Password sign-in answered something that is not Jellyfin", e)
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, passwordError = SetupError.Generic) ?: it }
            } catch (e: IOException) {
                Log.i(TAG, "Password sign-in failed", e)
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, passwordError = SetupError.Unreachable) ?: it }
            }
        }
    }

    /** Shows a code, then polls until the user approves it in a signed-in Jellyfin app, as Seerr's Plex PIN flow does. */
    fun startQuickConnect() {
        val current = _state.value as? UiState.SignIn ?: return
        val url = JellyfinClient.parseBaseUrl(current.baseUrl) ?: return
        quickConnect?.cancel()
        // The last failure goes as the new request starts, so a retry shows progress, not the old error.
        _state.value = current.copy(quickConnectCode = null, quickConnectError = null, gettingCode = true)
        quickConnect =
            viewModelScope.launch {
                try {
                    awaitQuickConnect(url, current.server)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    Log.i(TAG, "Quick Connect refused", e)
                    _state.failQuickConnect(SetupError.QuickConnectExpired)
                } catch (e: JellyfinUnreadableAnswer) {
                    Log.i(TAG, "Quick Connect answered something that is not Jellyfin", e)
                    _state.failQuickConnect(SetupError.Generic)
                } catch (e: IOException) {
                    Log.i(TAG, "Quick Connect failed", e)
                    _state.failQuickConnect(SetupError.Unreachable)
                }
            }
    }

    private suspend fun awaitQuickConnect(
        url: HttpUrl,
        server: PublicServerInfo,
    ) {
        val client = connection.anonymous(url)
        if (!client.quickConnectEnabled()) {
            _state.failQuickConnect(SetupError.QuickConnectOff)
            return
        }
        val start = client.quickConnectInitiate()
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = start.code, gettingCode = false) ?: it }
        val deadline = System.currentTimeMillis() + QUICK_CONNECT_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            delay(quickConnectPollMillis)
            if (client.quickConnectAuthenticated(start.secret)) {
                finish(url, server, client.authenticateWithQuickConnect(start.secret))
                return
            }
        }
        _state.failQuickConnect(SetupError.QuickConnectExpired)
    }

    /** Leaves Quick Connect for the password form, taking Quick Connect's code and failure with it. */
    fun cancelQuickConnect() {
        quickConnect?.cancel()
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, quickConnectError = null, gettingCode = false) ?: it }
    }

    fun changeServer() {
        quickConnect?.cancel()
        val address = (_state.value as? UiState.SignIn)?.baseUrl.orEmpty()
        _state.value = UiState.EnterServer(address = address)
    }

    /**
     * Reads the gates, then the library index. With [gatesOnly], reads the gates again and only them:
     * when a console page closes (it may have changed one, as turning Quick Connect off does) and when
     * the app comes back (the server or the user's rights may have changed elsewhere). That read is
     * quiet: one that fails keeps the gates as they were, and a full refresh says why.
     */
    fun refresh(gatesOnly: Boolean = false) {
        val current = _state.value as? UiState.SignedIn ?: return
        if (gatesOnly) {
            viewModelScope.launch {
                val (session, client) = connection.signedIn() ?: return@launch
                attempt { client.readGates(session.serverVersion) }.onSuccess { (profile, account) ->
                    _state.update { (it as? UiState.SignedIn)?.copy(profile = profile, account = account) ?: it }
                }
            }
            return
        }
        _state.value = current.copy(refreshing = true, refreshFailed = false)
        viewModelScope.launch {
            val failed =
                try {
                    val signedIn = connection.signedIn()
                    if (signedIn == null) {
                        _state.update { (it as? UiState.SignedIn)?.copy(refreshing = false) ?: it }
                        return@launch
                    }
                    // The gates first, so a library index that fails can't leave them unread.
                    val gates = signedIn.second.readGates(signedIn.first.serverVersion)
                    _state.update { (it as? UiState.SignedIn)?.copy(profile = gates.first, account = gates.second) ?: it }
                    index.refresh(signedIn.second, signedIn.first.userId)
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
            // The failure belongs to the method that got this far: Quick Connect holds a code, a password does not.
            _state.update {
                val signIn = it as? UiState.SignIn ?: return@update it
                if (signIn.quickConnectCode != null) {
                    signIn.copy(quickConnectCode = null, gettingCode = false, quickConnectError = SetupError.Generic)
                } else {
                    signIn.copy(busy = false, passwordError = SetupError.Generic)
                }
            }
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

/** Quick Connect couldn't get or keep a code: the code goes, and the failure stays with Quick Connect. */
private fun MutableStateFlow<UiState>.failQuickConnect(error: SetupError) =
    update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, gettingCode = false, quickConnectError = error) ?: it }

/**
 * The console's gates: the server's profile (its current version, which may have moved since sign-in,
 * and whether Quick Connect is on) and the user's account. Only the account read may fail the refresh.
 */
private suspend fun JellyfinClient.readGates(signedInVersion: String): Pair<JellyfinServerProfile, JellyfinAccount> {
    val account = me()
    val version = attempt { publicInfo().version }.getOrNull() ?: signedInVersion
    val quickConnect = attempt { quickConnectEnabled() }.getOrDefault(true)
    return JellyfinServerProfile.from(version, quickConnect) to account
}
