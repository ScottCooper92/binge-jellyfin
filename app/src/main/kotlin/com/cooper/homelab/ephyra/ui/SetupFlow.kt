package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.presentation.connect.Authenticator
import com.cooper.homelab.core.presentation.connect.ConnectEvent
import com.cooper.homelab.core.presentation.connect.ConnectLinks
import com.cooper.homelab.core.presentation.connect.ConnectPorts
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ConnectViewModel
import com.cooper.homelab.core.presentation.connect.LinkStash
import com.cooper.homelab.core.presentation.connect.LinkState
import com.cooper.homelab.core.presentation.connect.SessionSink
import com.cooper.homelab.core.server.address.PortDefaults
import com.cooper.homelab.core.server.connection.SecretCipher
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.demo.isDemoOrigin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.telemetry.SignInReports
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The address and sign-in steps: the shared connect machine ([flow]) and its code sign-ins ([links]), with this app's probe, its two
 * ways in, its demo and its session sink, and the projection of both onto [state], the setup half the screens read. It also holds the
 * ends of a setup: a sign-in that began a session ([publish]), a session that ended ([leave]), and the wizard's own sign-in ([finish]).
 */
internal class SetupFlow(
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val connection: Connection,
    sessions: SessionStore,
    private val handoff: SessionHandoff,
    localNetwork: LocalNetworkPermission,
    cleartext: CleartextConsent,
    cipher: SecretCipher,
    linkStash: LinkStash,
    quickConnectPollMillis: Long,
    private val reports: SignInReports,
    private val state: MutableStateFlow<UiState.Setup>,
    private val signedInFlow: MutableStateFlow<UiState.SignedIn?>,
    private val refresh: () -> Unit,
) {
    private val probe = JellyfinProbe(connection)

    private val demo = DemoSignIn(connection, probe, reports) { server, signedIn -> sink.save(server, signedIn) }

    /** Where a sign-in of the connect flow ends: the session is kept and the signed-in state takes the screen. */
    private val sink =
        SessionSink<PublicServerInfo, SignedIn> { server, signedIn ->
            handoff.save(server.preview.baseUrl, server.preview, signedIn)?.let(::publish) != null
        }

    /** The address and sign-in steps. */
    val flow =
        ConnectViewModel<PublicServerInfo, SignedIn>(
            scope = scope,
            dispatcher = dispatcher,
            probe = probe,
            authenticators =
                mapOf(
                    SetupMode.Password.id to
                        Authenticator { server, form ->
                            connection.passwordSignIn(server.preview.baseUrl, form.value(USERNAME_FIELD), form.value(PASSWORD_FIELD))
                        },
                ),
            sink = sink,
            classify = { it.setupFailure() },
            ports = ConnectPorts(PortDefaults.JELLYFIN_HTTP, PortDefaults.JELLYFIN_HTTPS),
            cleartext = cleartext,
            consentHost = { it.url.toHttpUrl().host },
            localNetwork = localNetwork,
            saved = sessions.session.map { it != null },
            events = ::count,
            demo = demo::enter,
        )

    /** Quick Connect, the sign-in that finishes in another Jellyfin app. */
    val links =
        ConnectLinks(
            scope = scope,
            dispatcher = dispatcher,
            machine = flow,
            providers = mapOf(SetupMode.QuickConnect.id to JellyfinQuickConnect(connection, quickConnectPollMillis)),
            sink = sink,
            stash = linkStash,
            cipher = cipher,
            events = ::count,
        )

    /** What the password form held when Quick Connect was chosen, so leaving Quick Connect gives it back. */
    var typed: Map<String, String> = emptyMap()

    /** The name to put in the sign-in step once the server has been read again: the account the wizard made, whose sign-in failed. */
    private var pendingName: String? = null

    init {
        scope.launch {
            combine(flow.state, links.link) { connect, link -> connect to link }.collect { (connect, link) -> render(connect, link) }
        }
    }

    /** Begins the session that was saved before this process started. */
    fun restore(session: JellyfinSession) {
        flow.editAddress(addressAfter(session.baseUrl))
        signedInFlow.value = signedInState(session)
        refresh()
    }

    /** Lays the connect flow's state over the setup half, for the screens. The signed-in state, once there is one, is left alone. */
    fun render(
        connect: ConnectState<PublicServerInfo>,
        link: LinkState?,
    ) {
        // The sign-in step starts on the password form, which the flow does not choose for itself.
        if (connect is ConnectState.SignIn && connect.form.mode == null) {
            flow.selectMode(SetupMode.Password)
            return
        }
        if (connect is ConnectState.SignIn) {
            pendingName?.let { name ->
                pendingName = null
                flow.editForm { with(USERNAME_FIELD, name) }
                return
            }
        }
        var entered: UiState.SignIn? = null
        state.update { current ->
            if (signedInFlow.value != null) return@update current
            projectSetup(connect, link, current).also { next ->
                entered = (next as? UiState.SignIn)?.takeIf { (current as? UiState.SignIn)?.baseUrl != it.baseUrl }
            }
        }
        entered?.let { scope.loadPublicUsers(connection, state, it.server.baseUrl, it.baseUrl) }
    }

    /** Lays the flow's state over the setup half as it stands now. */
    fun renderNow() = render(flow.state.value, links.link.value)

    /** Back from the wizard to the notice it was opened from: the flow still holds that server, so it is shown again as it was. */
    fun showSignInAgain() {
        state.value = UiState.Loading
        renderNow()
    }

    /** The wizard's own sign-in did not end in a session: the server is read again, now set up, and the user signs in by hand. */
    fun signInByHand(
        name: String,
        baseUrl: String,
    ) {
        pendingName = name
        state.value = UiState.Loading
        flow.editAddress(baseUrl)
        flow.changeServer()
        flow.inspect(demo::handOff)
    }

    /** The password a reset has just set, in the password field. */
    fun fillPassword(password: String) {
        if (flow.draft.value.server != null) flow.editForm { with(PASSWORD_FIELD, password) }
    }

    /** Reads the server at the typed address. The demo's address signs in to the demo instead of asking for an account. */
    fun inspect() = flow.inspect(demo::handOff)

    /** A sign-in has ended in a kept session: the signed-in state takes the screen, and the steps that led here are gone. */
    private fun publish(begun: UiState.SignedIn) {
        // The server step that comes back when the session ends starts at this server's address, from its first frame.
        flow.editAddress(addressAfter(begun.session.baseUrl))
        signedInFlow.value = begun
        // The step that led here goes with it: its typed password and the account pictures are gone when the step is left.
        state.value = UiState.Loading
        refresh()
    }

    /** The saved session stays saved, but the signed-in state goes: the server it is for is being edited. */
    fun leaveSavedSession() {
        signedInFlow.value = null
    }

    /** The address the server step starts at once the session of [baseUrl] has ended: the server's own, and none for the demo's, which is no server of the user's. */
    private fun addressAfter(baseUrl: String): String = baseUrl.takeUnless { it.isDemoOrigin() }.orEmpty()

    /** The session has ended: the server step takes the screen with the address it had, and the signed-in state goes. */
    fun leave(baseUrl: String) {
        signedInFlow.value = null
        state.value = UiState.Loading
        typed = emptyMap()
        flow.editAddress(addressAfter(baseUrl))
        flow.changeServer()
        // The flow may already have been at this step, in which case it has nothing new to say.
        renderNow()
    }

    /** Ends a sign-in the first-run wizard made: the session is kept and counted, and the signed-in state takes the screen. */
    suspend fun finish(
        url: HttpUrl,
        server: PublicServerInfo,
        signedIn: SignedIn,
        method: String,
    ) {
        val begun = handoff.save(url, server, signedIn)
        if (begun == null) {
            reports.failed(method, SetupError.Generic)
            // Setup is complete but the session could not be kept: the last step stays, with the reason.
            state.update {
                (it as? UiState.Startup)?.copy(
                    phase = (it.phase as? StartupPhase.Finish)?.copy(busy = false, error = SetupError.Generic) ?: it.phase,
                ) ?: it
            }
            return
        }
        reports.signedIn(method)
        publish(begun)
    }

    /** What the connect flow did that is counted: a sign-in that worked, and why one did not. Never an address or a name. */
    private fun count(event: ConnectEvent) {
        when (event) {
            is ConnectEvent.SignedIn -> if (event.success) reports.signedIn(event.mode)
            is ConnectEvent.SignInFailed -> reports.failed(event.mode, event.error.toSetupError())
            is ConnectEvent.LinkStarted -> Unit
        }
    }
}
