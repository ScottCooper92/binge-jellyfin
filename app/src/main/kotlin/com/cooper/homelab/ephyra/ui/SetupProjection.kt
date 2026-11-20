package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.LinkState
import com.cooper.homelab.core.presentation.connect.ProbedServer
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
    )
}
