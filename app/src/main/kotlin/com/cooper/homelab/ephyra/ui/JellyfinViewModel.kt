package com.cooper.homelab.ephyra.ui

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.presentation.connect.LinkStash
import com.cooper.homelab.core.server.connection.SecretCipher
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscoveredServer
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.PublicUser
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.server.localNetworkHostOrNull
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.server.publicUsers
import com.cooper.homelab.ephyra.server.userThumbnail
import com.cooper.homelab.ephyra.telemetry.AnalyticsEvents
import com.cooper.homelab.ephyra.telemetry.SignInReports
import com.cooper.homelab.ephyra.ui.console.decodeThumbnail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.io.IOException
import com.cooper.homelab.core.server.connection.ConnectionHealth as CoreConnectionHealth

/** Why a step failed, as the screen words it. */
enum class SetupError {
    InvalidAddress,
    Unreachable,
    LocalNetworkDenied,
    NotJellyfin,
    WrongCredentials,
    QuickConnectOff,
    QuickConnectExpired,

    /** Plain HTTP to a public host, which the app refuses unless the user has agreed to it. */
    CleartextRefused,

    /** The server asks for a client certificate, which this app cannot send yet. */
    ClientCertificateNeeded,
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

    /** The server asks for a client certificate, which this app cannot send yet: a retry on the same address cannot fix it. */
    ClientCertificateNeeded,
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
    cause: IOException? = null,
): ConnectionHealth =
    when {
        cause != null && asksForClientCertificate(listOf(cause)) -> ConnectionHealth.ClientCertificateNeeded
        baseUrl.localNetworkHostOrNull() != null && !localNetwork.isGranted() -> ConnectionHealth.LocalNetworkDenied
        else -> ConnectionHealth.Unreachable
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
internal fun CoroutineScope.loadPublicUsers(
    connection: Connection,
    state: MutableStateFlow<UiState.Setup>,
    url: HttpUrl,
    base: String,
) {
    launch {
        attempt { connection.anonymous(url).publicUsers() }
            .onSuccess { users ->
                // Only for the step it was asked for: the user may have changed server while it was in flight.
                state.update { (it as? UiState.SignIn)?.takeIf { s -> s.baseUrl == base }?.copy(publicUsers = users) ?: it }
                loadPublicPictures(connection, state, url, users)
            }.onFailure { connection.logger.noted("JellyfinSetup", "the server's public users could not be read", it) }
    }
}

/**
 * Reads each listed account's picture for the sign-in step. There is no token yet, so the client is the anonymous one and the
 * route is the one the server answers to anyone; a picture that cannot be read stays the account's initials. A picture lands only
 * in the step whose list it was asked for, and is held in memory alone.
 */
private suspend fun loadPublicPictures(
    connection: Connection,
    state: MutableStateFlow<UiState.Setup>,
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

/**
 * The user has answered the local-network prompt, or come back from the app's settings: try again whatever it was that
 * failed for want of it. The server step connects again; a signed-in server is read again; sign-in leaves the next press
 * of its own button to the user, who may have a password half typed.
 */
fun JellyfinViewModel.localNetworkAnswered() {
    when (state.value) {
        is UiState.EnterServer -> localNetworkResult()
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
internal fun UiState.SignedIn.afterReach(
    reach: CoreConnectionHealth,
    localNetwork: LocalNetworkPermission,
): UiState.SignedIn {
    val signedIn = this
    return when {
        reach == CoreConnectionHealth.Unreachable && signedIn.health == ConnectionHealth.Healthy ->
            signedIn.copy(health = unreachableHealth(signedIn.session.baseUrl, localNetwork), refreshFailed = true)
        reach == CoreConnectionHealth.Healthy && signedIn.health == ConnectionHealth.Unreachable ->
            signedIn.copy(health = ConnectionHealth.Healthy, refreshFailed = false)
        else -> signedIn
    }
}

sealed interface UiState {
    /** The steps before a session exists: the one flow the setup half writes, which can never hold a [SignedIn]. */
    sealed interface Setup : UiState

    data object Loading : Setup

    /** Step one: the server's address. */
    data class EnterServer(
        val address: String = "",
        val checking: Boolean = false,
        val error: SetupError? = null,
        /** The servers that answered on the local network, and whether the search is still running. */
        val discovered: List<DiscoveredServer> = emptyList(),
        val discovering: Boolean = false,
        /** The public host the typed address would reach over plain HTTP, or null: the user must agree to it before anything is sent. */
        val insecureHost: String? = null,
        /** The user has agreed to plain HTTP to [insecureHost]. */
        val cleartextAllowed: Boolean = false,
    ) : Setup {
        /** Whether Connect can go: an address is typed, and a plain-HTTP public host has been agreed to. */
        val canConnect: Boolean get() = address.isNotBlank() && (insecureHost == null || cleartextAllowed)
    }

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
        /** The method the tab row has chosen: Quick Connect when true, the password's form when not. */
        val quickConnectSelected: Boolean = false,
    ) : Setup {
        val cleartext: Boolean get() = baseUrl.startsWith("http://")
    }

    /** The setup wizard of a server that has not finished its first-run setup, reached from the sign-in step's notice. */
    data class Startup(
        val baseUrl: String,
        val server: PublicServerInfo,
        val phase: StartupPhase = StartupPhase.Loading,
    ) : Setup

    data class SignedIn(
        val session: JellyfinSession,
        val titles: Int? = null,
        val refreshing: Boolean = false,
        val refreshFailed: Boolean = false,
        /** What the last full read said of the server; a problem takes the place of the home. */
        val health: ConnectionHealth = ConnectionHealth.Checking,
        val confirmingSignOut: Boolean = false,
        /**
         * The public host this session reaches over plain HTTP, which the user agreed to on the server step and may take back (the
         * server card's "Not encrypted" mark). Null for an HTTPS, local or private host, and where the address policy is off: there
         * is no agreement to take back.
         */
        val consentHost: String? = null,
        /** The take-back confirm is showing. */
        val confirmingTakeBack: Boolean = false,
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

/**
 * The app's one screen state machine: server, sign-in (password or Quick Connect), then the signed-in settings. The first two steps are
 * the shared connect flow's, in [SetupFlow]; what the screens read of them is [projectSetup], in the shapes they have always taken.
 */
class JellyfinViewModel(
    private val sessions: SessionStore,
    private val connection: Connection,
    private val index: LibraryIndex,
    /** Where every read and sign-in of the connect flow runs. */
    private val dispatcher: CoroutineDispatcher,
    /** Seals what a Quick Connect wait keeps, should the app keep it. */
    private val cipher: SecretCipher,
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
    /** Where the user's agreement to plain HTTP to a public host is kept. Nothing is agreed to by default. */
    private val cleartext: CleartextConsent = CleartextConsent.None,
    /** Where a Quick Connect wait is kept so it outlives the process; nothing is kept by default. */
    private val linkStash: LinkStash = NoLinkStash,
    /** Where the typed address and the step reached are kept across the process being killed; nothing survives by default. */
    savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    /** The steps before a session exists. The setup half writes this and never a signed-in state. */
    private val _state = MutableStateFlow<UiState.Setup>(UiState.Loading)

    /** The setup half as it stands, whatever the screens show: for tests, which check that a finished sign-in leaves nothing behind. */
    internal val setupStep: StateFlow<UiState.Setup> get() = _state

    /** The signed-in state, or null with no session. [SignedInSession] writes this and never a setup step. */
    private val signedInFlow = MutableStateFlow<UiState.SignedIn?>(null)

    /** What the screens read: the signed-in state while a session is held, the setup step otherwise. */
    val state: StateFlow<UiState> = ScreenState(_state, signedInFlow)

    private val reports = SignInReports(analytics)

    /** Refreshing, the gates, the server's health and signing out: everything from a saved session on. */
    private val signedIn: SignedInSession =
        SignedInSession(
            viewModelScope,
            signedInFlow,
            sessions,
            connection,
            index,
            card,
            localNetwork,
            reports,
            onSessionEnded,
            leave = { setup.leave(it) },
        )

    /** The address and sign-in steps, and where they end. */
    internal val setup: SetupFlow =
        SetupFlow(
            scope = viewModelScope,
            dispatcher = dispatcher,
            connection = connection,
            sessions = sessions,
            handoff = SessionHandoff(sessions, index),
            localNetwork = localNetwork,
            cleartext = cleartext,
            cipher = cipher,
            linkStash = linkStash,
            quickConnectPollMillis = quickConnectPollMillis,
            reports = reports,
            state = _state,
            signedInFlow = signedInFlow,
            refresh = { signedIn.refresh() },
        )

    /** What the setup screens ask of the setup half. The tab row and a code's Cancel go to it directly, as the class has no room for more. */
    internal val actions = SetupActions(setup, _state, viewModelScope, { signedInFlow.value }, discover)

    /** The setup wizard for a server that has not finished its first-run setup. */
    val startup =
        StartupWizard(
            connection,
            _state,
            viewModelScope,
            localNetwork,
            onBack = setup::showSignInAgain,
            signInByHand = setup::signInByHand,
        ) { url, server, signedIn -> setup.finish(url, server, signedIn, AnalyticsEvents.METHOD_PASSWORD) }

    /** The forgotten-password steps of the sign-in step. */
    val forgotPassword = ForgotPasswordFlow(viewModelScope, connection, _state, fillPassword = setup::fillPassword)

    private val drafts = SetupDrafts(savedState)

    init {
        viewModelScope.launch {
            val session = sessions.current()
            // A session is the app's own state to come back to; the steps before one are what the saved draft puts back.
            if (session != null) setup.restore(session) else drafts.restoreInto(setup)
            // Nothing is kept once a session exists: the draft is for the steps that lead to one.
            drafts.keep(combine(setup.flow.draft, signedInFlow) { draft, signedIn -> draft.takeIf { signedIn == null } })
        }
    }

    /** Reads the gates, then the library index; with [gatesOnly], the gates alone. See [SignedInSession.refresh]. */
    fun refresh(gatesOnly: Boolean = false) = signedIn.refresh(gatesOnly)

    /** Changes the signed-in state, and leaves any other step as it is. */
    internal fun updateSignedIn(change: (UiState.SignedIn) -> UiState.SignedIn) = signedIn.updateSignedIn(change)

    /** Ends the session here and on the server. See [SignedInSession.signOut]. */
    fun signOut() = signedIn.signOut()

    fun onAddress(value: String) = actions.onAddress(value)

    /** The user's agreement, or its withdrawal, to plain HTTP to the public host the typed address names. */
    fun allowCleartext(allowed: Boolean) = actions.allowCleartext(allowed)

    fun onUsername(value: String) = actions.onUsername(value)

    fun onPassword(value: String) = actions.onPassword(value)

    /** Looks for servers on the local network once per visit to the server step. A server is one tap away; nothing found shows nothing. */
    fun discoverServers() = actions.discoverServers()

    /** Reads the server at the typed address. The demo's address signs in to the demo instead of asking for an account. */
    fun connect() = actions.connect()

    fun signInWithPassword() = actions.signInWithPassword()

    /** Shows a code, then polls until the user approves it in a signed-in Jellyfin app, as Seerr's Plex PIN flow does. */
    fun startQuickConnect() = actions.startQuickConnect()

    /** Leaves Quick Connect for the password form, taking Quick Connect's code and failure with it. */
    fun cancelQuickConnect() = actions.cancelQuickConnect()

    /** The server step again, with the address filled in. See [SetupActions.changeServer]. */
    fun changeServer(recheck: Boolean = false) = actions.changeServer(recheck)

    internal fun tryDemo() = actions.tryDemo()

    internal fun localNetworkResult() = actions.localNetworkResult()

    private companion object {
        const val QUICK_CONNECT_POLL_MILLIS = 3_000L
    }
}

/**
 * The state the screens read: the signed-in state while a session is held, the setup step otherwise. Its [value] is read from the
 * two halves each time, so it is never behind a write to either of them, as one flow's value is not.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class ScreenState(
    private val setup: StateFlow<UiState.Setup>,
    private val signedIn: StateFlow<UiState.SignedIn?>,
) : StateFlow<UiState> {
    override val value: UiState get() = signedIn.value ?: setup.value

    override val replayCache: List<UiState> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<UiState>): Nothing {
        combine(setup, signedIn) { step, session -> session ?: step }.distinctUntilChanged().collect(collector)
        error("a state flow's collection never completes")
    }
}
