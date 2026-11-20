package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.LinkState
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.ephyra.server.PublicServerInfo

/** Why a connect step failed, in the words of this app's screens. */
internal fun ConnectError.toSetupError(): SetupError =
    when (this) {
        ConnectError.InvalidAddress -> SetupError.InvalidAddress
        ConnectError.NotThisServer -> SetupError.NotJellyfin
        ConnectError.LinkExpired -> SetupError.QuickConnectExpired
        ConnectError.LinkNotOffered -> SetupError.QuickConnectOff
        is ConnectError.Failed -> failure.toSetupError()
    }

private fun ServerFailure.toSetupError(): SetupError =
    when (this) {
        is ServerFailure.Unreachable -> SetupError.Unreachable
        ServerFailure.LocalNetworkDenied -> SetupError.LocalNetworkDenied
        ServerFailure.ClientCertificateNeeded -> SetupError.ClientCertificateNeeded
        ServerFailure.CleartextBlocked -> SetupError.CleartextRefused
        ServerFailure.SessionRejected -> SetupError.WrongCredentials
        else -> SetupError.Generic
    }

/** The address the sign-in step is for: as the server answered, without the trailing slash. */
private fun ProbedServer<PublicServerInfo>.base(): String = address.url.trimEnd('/')

/**
 * What the setup screens show of the connect flow, in the shapes they have always taken. The flow's own state fills every field it
 * has an answer for; what only this app knows (the servers found on the network, the accounts the server lists and their pictures, a
 * password reset under way) is carried over from [current] while it is still for the same step. A wizard that is open is the
 * wizard's, so it is left as it is.
 */
internal fun projectSetup(
    connect: ConnectState<PublicServerInfo>,
    link: LinkState?,
    current: UiState.Setup,
): UiState.Setup =
    when {
        current is UiState.Startup -> current
        connect is ConnectState.Address -> {
            val before = current as? UiState.EnterServer
            UiState.EnterServer(
                address = connect.address,
                checking = connect.isInspecting,
                error = connect.error?.toSetupError(),
                discovered = before?.discovered.orEmpty(),
                discovering = before?.discovering ?: false,
                insecureHost = if (connect.insecure) ServerAddress.parse(connect.address)?.host else null,
                cleartextAllowed = connect.cleartextAllowed,
            )
        }
        connect is ConnectState.SignIn -> signIn(connect.server, connect.form, connect.isConnecting, connect.error, link, current)
        connect is ConnectState.NotSetUp -> signIn(connect.server, null, false, connect.error, null, current)
        else -> UiState.Loading
    }

private fun signIn(
    server: ProbedServer<PublicServerInfo>,
    form: com.cooper.homelab.core.presentation.connect.SignInForm?,
    connecting: Boolean,
    error: ConnectError?,
    link: LinkState?,
    current: UiState.Setup,
): UiState.SignIn {
    val base = server.base()
    val before = (current as? UiState.SignIn)?.takeIf { it.baseUrl == base }
    val quickConnect = form?.mode == SetupMode.QuickConnect
    val failure = error?.toSetupError()
    return UiState.SignIn(
        baseUrl = base,
        server = server.preview,
        username = form?.value(USERNAME_FIELD).orEmpty(),
        password = form?.value(PASSWORD_FIELD).orEmpty(),
        busy = connecting && !quickConnect,
        quickConnectCode = link?.code,
        passwordError = failure.takeUnless { quickConnect },
        quickConnectError = failure.takeIf { quickConnect },
        gettingCode = connecting && quickConnect && link == null,
        forgot = before?.forgot,
        publicUsers = before?.publicUsers.orEmpty(),
        publicPictures = before?.publicPictures.orEmpty(),
        quickConnectOn = SetupMode.QuickConnect in server.modes,
        quickConnectSelected = quickConnect,
    )
}

/** The flow's error for what the screens hold, so a shared step can show it: the reverse of [toSetupError]. */
internal fun SetupError.toConnectError(): ConnectError =
    when (this) {
        SetupError.InvalidAddress -> ConnectError.InvalidAddress
        SetupError.NotJellyfin -> ConnectError.NotThisServer
        SetupError.QuickConnectExpired -> ConnectError.LinkExpired
        SetupError.QuickConnectOff -> ConnectError.LinkNotOffered
        SetupError.Unreachable -> ConnectError.Failed(ServerFailure.Unreachable())
        SetupError.LocalNetworkDenied -> ConnectError.Failed(ServerFailure.LocalNetworkDenied)
        SetupError.ClientCertificateNeeded -> ConnectError.Failed(ServerFailure.ClientCertificateNeeded)
        SetupError.CleartextRefused -> ConnectError.Failed(ServerFailure.CleartextBlocked)
        SetupError.WrongCredentials -> ConnectError.Failed(ServerFailure.SessionRejected)
        SetupError.Generic -> ConnectError.Failed(ServerFailure.Unknown)
    }

/** The address step the shared screen draws for this step of the server step, with the local-network ask where that is what failed. */
internal fun UiState.EnterServer.toConnectState(): ConnectState.Address =
    ConnectState.Address(
        address = address,
        insecure = insecureHost != null,
        cleartextAllowed = cleartextAllowed,
        isInspecting = checking,
        error = error?.toConnectError(),
        needsLocalNetwork = error == SetupError.LocalNetworkDenied,
        canTryDemo = true,
    )

private const val UNPARSED_ADDRESS = "http://invalid.invalid"

/**
 * The second step the shared screen draws for this sign-in step: the form of the tab chosen, the password's (the one the screens open
 * on) or Quick Connect's, which has no fields, or the notice of a server that is not set up. A code being waited on is [linkState]'s,
 * and keeps the step busy.
 */
internal fun UiState.SignIn.toConnectState(): ConnectState<PublicServerInfo> {
    val address = ServerAddress.parse(baseUrl) ?: requireNotNull(ServerAddress.parse(UNPARSED_ADDRESS))
    val modes = if (quickConnectOn) listOf(SetupMode.Password, SetupMode.QuickConnect) else listOf(SetupMode.Password)
    val probed = ProbedServer(address, server.serverName.ifBlank { baseUrl }, !server.setupIncomplete, modes, server)
    val failure = (passwordError ?: quickConnectError)?.toConnectError()
    if (server.setupIncomplete) return ConnectState.NotSetUp(probed, isChecking = false, error = failure)
    val form =
        if (quickConnectSelected) {
            SignInForm(SetupMode.QuickConnect)
        } else {
            SignInForm(SetupMode.Password, mapOf(USERNAME_FIELD to username, PASSWORD_FIELD to password))
        }
    return ConnectState.SignIn(probed, form, isConnecting = busy || gettingCode || quickConnectCode != null, error = failure)
}

/** The code the screen shows while Quick Connect waits for its approval, or null. */
internal fun UiState.SignIn.linkState(): LinkState? = quickConnectCode?.let { LinkState(it, openUrl = null, launchPending = false) }

/** What changed between this form and [after], by field id: the screens report each as the user's edit of that field. */
internal fun SignInForm.changesTo(after: SignInForm): Map<String, String> = after.values.filter { (field, value) -> value(field) != value }
