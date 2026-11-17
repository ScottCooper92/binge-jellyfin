package com.cooper.binge.companion.jellyfin.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.demo.DEMO_ORIGIN
import com.cooper.binge.companion.jellyfin.demo.DEMO_USER_NAME
import com.cooper.binge.companion.jellyfin.demo.isDemoOrigin
import com.cooper.binge.companion.jellyfin.server.AddressPolicy
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.JellyfinUnreadableAnswer
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.server.PublicUser
import com.cooper.binge.companion.jellyfin.server.Reach
import com.cooper.binge.companion.jellyfin.server.SignedIn
import com.cooper.binge.companion.jellyfin.server.authenticateByName
import com.cooper.binge.companion.jellyfin.server.authenticateWithQuickConnect
import com.cooper.binge.companion.jellyfin.server.localNetworkHostOrNull
import com.cooper.binge.companion.jellyfin.server.logout
import com.cooper.binge.companion.jellyfin.server.me
import com.cooper.binge.companion.jellyfin.server.publicInfo
import com.cooper.binge.companion.jellyfin.server.publicUsers
import com.cooper.binge.companion.jellyfin.server.quickConnectAuthenticated
import com.cooper.binge.companion.jellyfin.server.quickConnectEnabled
import com.cooper.binge.companion.jellyfin.server.quickConnectEnabledOrNull
import com.cooper.binge.companion.jellyfin.server.quickConnectInitiate
import com.cooper.binge.companion.jellyfin.server.userThumbnail
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsEvents
import com.cooper.binge.companion.jellyfin.telemetry.SignInReports
import com.cooper.binge.companion.jellyfin.ui.console.decodeThumbnail
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.IOException

/** Why a step failed, as the screen words it. */
enum class SetupError {
    InvalidAddress,
    Unreachable,
    LocalNetworkDenied,
    NotJellyfin,
    WrongCredentials,
    QuickConnectOff,
    QuickConnectExpired,
    Generic,
}

/**
 * What the last read of the saved server said about it. The home shows the server's problem in place of itself while it
 * is [isProblem]: a server that did not answer, or one that answered with something the app could not use. A token the
 * server refuses is not here: that goes to sign-in.
 */
enum class ConnectionHealth {
    /** Nothing has answered yet, which is where every start begins. */
    Checking,

    /** The server answered and the app could read it. */
    Healthy,

    /** The server did not answer: offline, off the network, or the address has changed. Trying again may recover. */
    Unreachable,

    /** The server answered, but with an error or with something that is not Jellyfin. */
    CouldNotLoad,

    /** The server is on the local network and Android has not let the app reach it: a retry cannot fix it, only the permission can. */
    LocalNetworkDenied,
    ;

    val isProblem: Boolean get() = this != Checking && this != Healthy
}

/**
 * Why a server did not answer. The platform's refusal of the local network is a bare connection failure, so it is told
 * apart by who is being asked: a server on the local network, while the app does not hold the permission for it.
 */
internal fun unreachableHealth(
    baseUrl: String,
    localNetwork: LocalNetworkPermission,
): ConnectionHealth =
    if (baseUrl.localNetworkHostOrNull() != null && !localNetwork.isGranted()) {
        ConnectionHealth.LocalNetworkDenied
    } else {
        ConnectionHealth.Unreachable
    }

/** Why a connection to [baseUrl] failed while setting up: the permission for the local network, or the server itself. */
internal fun unreachableSetup(
    baseUrl: String,
    localNetwork: LocalNetworkPermission,
): SetupError =
    if (unreachableHealth(baseUrl, localNetwork) ==
        ConnectionHealth.LocalNetworkDenied
    ) {
        SetupError.LocalNetworkDenied
    } else {
        SetupError.Unreachable
    }

/** Picks a server found on the network: connects to it as if its address had been typed. */
fun JellyfinViewModel.chooseServer(server: DiscoveredServer) {
    if ((state.value as? UiState.EnterServer)?.checking == true) return
    onAddress(server.address)
    connect()
}

/** Picks a listed account: its name fills the username, and one with no password signs in straight away. */
fun JellyfinViewModel.pickPublicUser(user: PublicUser) {
    val current = state.value as? UiState.SignIn ?: return
    if (current.busy) return
    onUsername(user.name)
    onPassword("")
    if (!user.hasPassword) signInWithPassword()
}

/** Reads the server's public users once, for the sign-in step. A failure or an empty list leaves the step as it is. */
private fun CoroutineScope.loadPublicUsers(
    connection: Connection,
    state: MutableStateFlow<UiState>,
    url: HttpUrl,
    base: String,
) {
    launch {
        attempt { connection.anonymous(url).publicUsers() }
            .onSuccess { users ->
                // Only for the step it was asked for: the user may have changed server while it was in flight.
                state.update { (it as? UiState.SignIn)?.takeIf { s -> s.baseUrl == base }?.copy(publicUsers = users) ?: it }
                loadPublicPictures(connection, state, url, users)
            }.onFailure { Log.i("JellyfinSetup", "The server's public users could not be read", it) }
    }
}

/**
 * Reads whether the server has Quick Connect on, for the sign-in step. The route is public, so it is the anonymous client. Only an
 * answer of "off" changes the step: a failure or an unclear answer keeps Quick Connect offered. A code already asked for is
 * dropped without an error, since the user did not ask to be told.
 */
internal fun CoroutineScope.loadQuickConnectOn(
    connection: Connection,
    state: MutableStateFlow<UiState>,
    url: HttpUrl,
    base: String,
    onOff: () -> Unit,
) {
    launch {
        val on =
            attempt { connection.anonymous(url).quickConnectEnabledOrNull() }
                .onFailure { Log.i("JellyfinSetup", "Whether Quick Connect is on could not be read", it) }
                .getOrNull()
        // Only for the step it was asked for: the user may have moved to another server while it was in flight, and that server's
        // code request is not this answer's to cancel.
        if (on == false && (state.value as? UiState.SignIn)?.baseUrl == base) {
            // A code request already in flight stops first, so it cannot land a code or an error after the step says it is off.
            onOff()
            state.update { s ->
                (s as? UiState.SignIn)
                    ?.takeIf { it.baseUrl == base }
                    ?.copy(quickConnectOn = false, quickConnectCode = null, quickConnectError = null, gettingCode = false)
                    ?: s
            }
        }
    }
}

/**
 * Reads each listed account's picture for the sign-in step. There is no token yet, so the client is the anonymous one and the
 * route is the one the server answers to anyone; a picture that cannot be read stays the account's initials. A picture lands only
 * in the step whose list it was asked for, and is held in memory alone.
 */
private suspend fun loadPublicPictures(
    connection: Connection,
    state: MutableStateFlow<UiState>,
    url: HttpUrl,
    users: List<PublicUser>,
) {
    val client = connection.anonymous(url)
    val permits = Semaphore(CONCURRENT_PICTURES)
    coroutineScope {
        users.forEach { user ->
            val tag = user.imageTag ?: return@forEach
            launch {
                val picture =
                    permits.withPermit {
                        withContext(connection.io) {
                            attempt { client.userThumbnail(user.id, tag)?.let(::decodeThumbnail) }.getOrNull()
                        }
                    } ?: return@launch
                state.update { s ->
                    val step = (s as? UiState.SignIn)?.takeIf { it.publicUsers === users }
                    step?.copy(publicPictures = step.publicPictures + (user.id to picture)) ?: s
                }
            }
        }
    }
}

private const val CONCURRENT_PICTURES = 4

/** Opens the demo server, which answers from memory and signs in as its own administrator. */
fun JellyfinViewModel.tryDemo() {
    if ((state.value as? UiState.EnterServer)?.checking == true) return
    onAddress(DEMO_ORIGIN)
    connect()
}

/**
 * The user has answered the local-network prompt, or come back from the app's settings: try again whatever it was that
 * failed for want of it. The server step connects again; a signed-in server is read again; sign-in leaves the next press
 * of its own button to the user, who may have a password half typed.
 */
fun JellyfinViewModel.localNetworkAnswered() {
    when (state.value) {
        is UiState.EnterServer -> connect()
        is UiState.SignedIn -> refresh()
        else -> Unit
    }
}

/**
 * Moves the home's [ConnectionHealth] with what the live calls say, in two places only. A home that was fine becomes the
 * server's problem when a run of calls goes unanswered; a problem of that kind clears when one is answered. A server that
 * answered with something unusable, and one the app may not reach, are the read's to name and to clear, and a start's first
 * read is its own: [ConnectionHealth.Checking] is not touched here.
 */
internal fun UiState.afterReach(
    reach: Reach,
    localNetwork: LocalNetworkPermission,
): UiState {
    val signedIn = this as? UiState.SignedIn ?: return this
    return when {
        reach == Reach.Unreached && signedIn.health == ConnectionHealth.Healthy ->
            signedIn.copy(health = unreachableHealth(signedIn.session.baseUrl, localNetwork), refreshFailed = true)
        reach == Reach.Reached && signedIn.health == ConnectionHealth.Unreachable ->
            signedIn.copy(health = ConnectionHealth.Healthy, refreshFailed = false)
        else -> signedIn
    }
}

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
        /** Where a forgotten-password reset is, or null when none is under way. */
        val forgot: ForgotPasswordStep? = null,
        /** The accounts the server lists on its login screen. Empty when it lists none or could not be asked: nothing is shown then. */
        val publicUsers: List<PublicUser> = emptyList(),
        /**
         * The listed accounts' pictures, by user id, read without a token and held only here: leaving the step drops them, and
         * nothing is written to disk. An account missing from the map shows its initials.
         */
        val publicPictures: Map<String, Bitmap> = emptyMap(),
        /**
         * Whether Quick Connect is offered. True until the server has said it is off: a read that fails or answers nothing leaves it
         * on, and the server's own refusal when a code is asked for stays the backstop.
         */
        val quickConnectOn: Boolean = true,
    ) : UiState {
        val cleartext: Boolean get() = baseUrl.startsWith("http://")

        /**
         * Whether the sign-in step says what Quick Connect is for. Only while Quick Connect is untried: the server's answer that it is
         * off is read when the step opens, so the hint is never shown for a server that has it off, and once a code has failed or is
         * on its way the screen already says what to do.
         */
        val showsQuickConnectHint: Boolean get() = quickConnectOn && quickConnectError == null && !gettingCode
    }

    /** The setup wizard of a server that has not finished its first-run setup, reached from the sign-in step's notice. */
    data class Startup(
        val baseUrl: String,
        val server: PublicServerInfo,
        val phase: StartupPhase = StartupPhase.Loading,
    ) : UiState

    data class SignedIn(
        val session: JellyfinSession,
        val titles: Int? = null,
        val refreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        /** What the last full read said of the server; a problem takes the place of the home. */
        val health: ConnectionHealth = ConnectionHealth.Checking,
        val confirmingSignOut: Boolean = false,
        /** What the server is and has turned on: the version gate. Read again with each refresh. */
        val profile: JellyfinServerProfile = JellyfinServerProfile.from(session.serverVersion),
        /** What the signed-in user may do, from their own policy: the account gate. Read with each refresh. */
        val account: JellyfinAccount = JellyfinAccount(session.userName),
        /** What the server card says of the server and its library, as far as it has been read. */
        val card: ServerCardState = ServerCardState(),
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
    private val card: ServerCardLoader = ServerCardLoader(),
    /** Whether the platform lets the app reach the local network, read when a connection fails to say why. */
    private val localNetwork: LocalNetworkPermission = LocalNetworkPermission.AlwaysGranted,
    /** What the app reports about its own use, once the user has agreed; nothing by default. */
    private val analytics: Analytics = NoOpAnalytics,
    /** Runs when the session ends, by sign-out or a refused token: what the app cached about the account goes with it. */
    private val onSessionEnded: suspend () -> Unit = {},
    /** How a typed address becomes the addresses to try: the released behaviour unless the app passes the shared library's. */
    private val addressPolicy: AddressPolicy = AddressPolicy.Legacy,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** The setup wizard for a server that has not finished its first-run setup. */
    val startup =
        StartupWizard(connection, _state, viewModelScope, localNetwork) { url, server, signedIn ->
            finish(url, server, signedIn, AnalyticsEvents.METHOD_PASSWORD)
        }

    private val reports = SignInReports(analytics)

    private val addressCheck = AddressCheck(connection, localNetwork)

    private var quickConnect: Job? = null

    /** The forgotten-password steps of the sign-in step. */
    val forgotPassword = ForgotPasswordFlow(viewModelScope, connection, _state)

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
        // The saved server stopped answering, or began again, while the app is open: the home follows it, as binge-seerr's does.
        viewModelScope.launch { connection.monitor.reach.collect { reach -> _state.update { it.afterReach(reach, localNetwork) } } }
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
        // A check already in flight is the only one: a second would finish in either order and replace a later state.
        if (current.checking) return
        val candidates = addressPolicy.candidates(current.address)
        if (candidates.isEmpty()) {
            _state.value = current.copy(error = SetupError.InvalidAddress)
            return
        }
        _state.value = current.copy(checking = true, error = null)
        viewModelScope.launch {
            val found =
                when (val result = addressCheck.first(candidates)) {
                    is AddressCheck.Result.Found -> result
                    is AddressCheck.Result.Failed -> {
                        _state.update { (it as? UiState.EnterServer)?.copy(checking = false, error = result.error) ?: it }
                        return@launch
                    }
                }
            val url = found.url
            val info = found.info
            if (url.toString().isDemoOrigin() && info.baseUrl.toString().isDemoOrigin()) {
                // The demo has no account to ask for: it signs in as its own administrator, through the same calls.
                val demo = attempt { connection.anonymous(url).authenticateByName(DEMO_USER_NAME, "") }
                demo.onSuccess { finish(url, info, it, AnalyticsEvents.METHOD_DEMO) }
                demo.onFailure {
                    Log.i(TAG, "The demo sign-in failed", it)
                    reports.failed(AnalyticsEvents.METHOD_DEMO, SetupError.Generic)
                    _state.update { s ->
                        (s as? UiState.EnterServer)?.copy(checking = false, error = SetupError.Generic) ?: s
                    }
                }
            } else {
                val base = info.baseUrl.toString().trimEnd('/')
                _state.value = UiState.SignIn(baseUrl = base, server = info)
                viewModelScope.loadPublicUsers(connection, _state, info.baseUrl, base)
                viewModelScope.loadQuickConnectOn(connection, _state, info.baseUrl, base) { quickConnect?.cancel() }
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
                finish(
                    url,
                    current.server,
                    connection.anonymous(url).authenticateByName(current.username.trim(), current.password),
                    AnalyticsEvents.METHOD_PASSWORD,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: JellyfinHttpException) {
                Log.i(TAG, "Password sign-in refused", e)
                val error = if (e.code == HTTP_UNAUTHORIZED) SetupError.WrongCredentials else SetupError.Generic
                reports.failed(AnalyticsEvents.METHOD_PASSWORD, error)
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, passwordError = error) ?: it }
            } catch (e: JellyfinUnreadableAnswer) {
                Log.i(TAG, "Password sign-in answered something that is not Jellyfin", e)
                reports.failed(AnalyticsEvents.METHOD_PASSWORD, SetupError.Generic)
                _state.update { (it as? UiState.SignIn)?.copy(busy = false, passwordError = SetupError.Generic) ?: it }
            } catch (e: IOException) {
                Log.i(TAG, "Password sign-in failed", e)
                reports.failed(AnalyticsEvents.METHOD_PASSWORD, unreachableSetup(current.baseUrl, localNetwork))
                _state.update {
                    (it as? UiState.SignIn)?.copy(busy = false, passwordError = unreachableSetup(current.baseUrl, localNetwork))
                        ?: it
                }
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
                    _state.failQuickConnect(SetupError.QuickConnectExpired, reports)
                } catch (e: JellyfinUnreadableAnswer) {
                    Log.i(TAG, "Quick Connect answered something that is not Jellyfin", e)
                    _state.failQuickConnect(SetupError.Generic, reports)
                } catch (e: IOException) {
                    Log.i(TAG, "Quick Connect failed", e)
                    _state.failQuickConnect(unreachableSetup(current.baseUrl, localNetwork), reports)
                }
            }
    }

    private suspend fun awaitQuickConnect(
        url: HttpUrl,
        server: PublicServerInfo,
    ) {
        val client = connection.anonymous(url)
        if (!client.quickConnectEnabled()) {
            _state.failQuickConnect(SetupError.QuickConnectOff, reports)
            return
        }
        val start = client.quickConnectInitiate()
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = start.code, gettingCode = false) ?: it }
        val deadline = System.currentTimeMillis() + QUICK_CONNECT_WINDOW_MILLIS
        while (System.currentTimeMillis() < deadline) {
            delay(quickConnectPollMillis)
            if (client.quickConnectAuthenticated(start.secret)) {
                finish(url, server, client.authenticateWithQuickConnect(start.secret), AnalyticsEvents.METHOD_QUICK_CONNECT)
                return
            }
        }
        _state.failQuickConnect(SetupError.QuickConnectExpired, reports)
    }

    /** Leaves Quick Connect for the password form, taking Quick Connect's code and failure with it. */
    fun cancelQuickConnect() {
        quickConnect?.cancel()
        _state.update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, quickConnectError = null, gettingCode = false) ?: it }
    }

    /**
     * The server step again, with the address filled in: from the sign-in step, or from a saved server that cannot be
     * reached. A saved session stays saved until a new sign-in replaces it, so closing the app comes back to the gate.
     * With [recheck], from the sign-in step, the address is read again at once: the notice for a server that was not set up
     * says to finish that elsewhere, and this finds out whether it has been.
     */
    fun changeServer(recheck: Boolean = false) {
        quickConnect?.cancel()
        val address =
            when (val current = _state.value) {
                is UiState.SignIn -> current.baseUrl
                is UiState.Startup -> current.baseUrl
                is UiState.SignedIn -> current.session.baseUrl
                else -> ""
            }
        _state.value = UiState.EnterServer(address = address)
        if (recheck && address.isNotBlank()) connect()
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
            viewModelScope.refreshGates(connection, _state, card)
            return
        }
        // One read at a time: a second would end the first's spinner early, and the older of the two could write last.
        if (current.refreshing) return
        _state.value = current.copy(refreshing = true, refreshFailed = false)
        viewModelScope.launch {
            val health =
                try {
                    val signedIn = connection.signedIn()
                    if (signedIn == null) {
                        _state.update { (it as? UiState.SignedIn)?.copy(refreshing = false) ?: it }
                        return@launch
                    }
                    // The gates first, so a library index that fails can't leave them unread.
                    val gates = signedIn.second.readGates(signedIn.first.serverVersion)
                    // The server has answered: end the first check here, not when the index finishes. A health that is already a problem stays one.
                    _state.update {
                        (it as? UiState.SignedIn)?.copy(
                            profile = gates.first,
                            account = gates.second,
                            health = if (it.health == ConnectionHealth.Checking) ConnectionHealth.Healthy else it.health,
                        ) ?: it
                    }
                    card.refresh(viewModelScope, _state, signedIn.first, signedIn.second, gates.second, force = true)
                    index.refresh(signedIn.second, signedIn.first.userId)
                    ConnectionHealth.Healthy
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JellyfinHttpException) {
                    Log.i(TAG, "Library refresh refused", e)
                    if (e.code == HTTP_UNAUTHORIZED) {
                        sessionRefused(current.session)
                        return@launch
                    }
                    ConnectionHealth.CouldNotLoad
                } catch (e: JellyfinUnreadableAnswer) {
                    Log.i(TAG, "Library refresh answered something that is not Jellyfin", e)
                    ConnectionHealth.CouldNotLoad
                } catch (e: IOException) {
                    Log.i(TAG, "Library refresh failed", e)
                    unreachableHealth(current.session.baseUrl, localNetwork)
                }
            _state.update {
                (it as? UiState.SignedIn)?.copy(
                    refreshing = false,
                    refreshFailed = health.isProblem,
                    health = health,
                    titles = index.size,
                ) ?: it
            }
        }
    }

    /** The server no longer accepts the token (a revoked device, say): forget it and ask for a sign-in, as the Service's UNAUTHENTICATED does. */
    private suspend fun sessionRefused(session: JellyfinSession) {
        sessions.clear()
        index.clear()
        onSessionEnded()
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
            reports.signedOut()
            sessions.clear()
            index.clear()
            onSessionEnded()
            _state.value = UiState.EnterServer(address = signedIn?.first?.baseUrl.orEmpty())
        }
    }

    private suspend fun finish(
        url: HttpUrl,
        server: PublicServerInfo,
        signedIn: SignedIn,
        method: String,
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
            reports.failed(method, SetupError.Generic)
            // The failure belongs to the method that got this far: Quick Connect holds a code, a password does not.
            _state.update {
                when (it) {
                    is UiState.SignIn ->
                        if (it.quickConnectCode != null) {
                            it.copy(quickConnectCode = null, gettingCode = false, quickConnectError = SetupError.Generic)
                        } else {
                            it.copy(busy = false, passwordError = SetupError.Generic)
                        }
                    // The demo signs in from the server step, so that is where its failure has to land.
                    is UiState.EnterServer -> it.copy(checking = false, error = SetupError.Generic)
                    // Setup is complete but the session could not be kept: the last step stays, with the reason.
                    is UiState.Startup ->
                        it.copy(phase = (it.phase as? StartupPhase.Finish)?.copy(busy = false, error = SetupError.Generic) ?: it.phase)
                    else -> it
                }
            }
            return
        }
        index.clear()
        reports.signedIn(method)
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
private fun MutableStateFlow<UiState>.failQuickConnect(
    error: SetupError,
    reports: SignInReports,
) {
    reports.failed(AnalyticsEvents.METHOD_QUICK_CONNECT, error)
    update { (it as? UiState.SignIn)?.copy(quickConnectCode = null, gettingCode = false, quickConnectError = error) ?: it }
}

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

/** Reads what the server and the user allow again, and the server card with it, leaving the library alone. */
private fun CoroutineScope.refreshGates(
    connection: Connection,
    state: MutableStateFlow<UiState>,
    card: ServerCardLoader,
) {
    launch {
        val (session, client) = connection.signedIn() ?: return@launch
        attempt { client.readGates(session.serverVersion) }.onSuccess { (profile, account) ->
            state.update { (it as? UiState.SignedIn)?.copy(profile = profile, account = account) ?: it }
            card.refresh(this@refreshGates, state, session, client, account, force = false)
        }
    }
}
