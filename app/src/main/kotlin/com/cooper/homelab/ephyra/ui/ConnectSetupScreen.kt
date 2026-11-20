package com.cooper.homelab.ephyra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.StepHeading
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.LinkState
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.uiandroid.connect.AllowLocalNetwork
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectCopy
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkActions
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkContent
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkCopy
import com.cooper.homelab.core.uiandroid.connect.ConnectScreen
import com.cooper.homelab.core.uiandroid.connect.ConnectScreenText
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInOptions
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.ui.console.UserAvatar
import com.binge.designsystem.R as DesR

/** Lines a server row's marker and address may take: enough for the whole address at 2× font. */
private const val ADDRESS_LINES = 5

/** The example in the address field. */
private const val ADDRESS_EXAMPLE = "192.168.1.10:8096"

/**
 * The address step and the sign-in step, drawn by the shared connect screen from the state the setup half holds. The screen's
 * own pieces are the address field, the plain-HTTP agreement, the demo entry, the fields, the Sign in button and the code sheet;
 * this app adds the servers found on the network, the accounts the server lists, Quick Connect as a button under the form, the
 * reset of a forgotten password, and the first-run wizard's way in.
 */
@Composable
internal fun ConnectSetupScreen(
    state: UiState,
    actions: Actions,
) {
    val connect: ConnectState<PublicServerInfo> =
        when (state) {
            is UiState.EnterServer -> state.toConnectState()
            is UiState.SignIn -> state.toConnectState()
            else -> return
        }
    // A step sliding out is still drawn, with the slots below, after the state has moved to the other one: each keeps what it last had.
    var lastSignIn by remember { mutableStateOf<UiState.SignIn?>(null) }
    var lastEnterServer by remember { mutableStateOf<UiState.EnterServer?>(null) }
    val signIn = state as? UiState.SignIn
    val enterServer = state as? UiState.EnterServer
    signIn?.let { lastSignIn = it }
    enterServer?.let { lastEnterServer = it }
    val signInShown = signIn ?: lastSignIn
    val enterServerShown = enterServer ?: lastEnterServer
    // The servers on the network are looked for once per visit to the server step.
    LaunchedEffect(enterServer != null) { if (enterServer != null) actions.onDiscover() }
    val passwordFocus = remember { FocusRequester() }
    ConnectScreen(
        state = connect,
        link = signIn?.linkState(),
        copy = setupCopy(),
        text =
            ConnectScreenText(
                placeholder = ADDRESS_EXAMPLE,
                notThisServer = stringResource(R.string.error_not_jellyfin),
                notSetUpMessage = stringResource(R.string.setup_incomplete_body),
                linkCopy = { quickConnectCopy() },
            ),
        addressActions =
            ConnectAddressActions(
                onEditAddress = actions.onAddress,
                onAllowCleartext = actions.onAllowCleartext,
                onContinue = actions.onConnect,
                onTryDemo = actions.onTryDemo,
            ),
        signInActions = signInActions(signIn, actions),
        linkActions = ConnectLinkActions(onLaunched = {}, onCancel = actions.onCancelQuickConnect),
        addressHeading = {
            StepHeading(title = stringResource(R.string.setup_server_title), subtitle = stringResource(R.string.setup_server_body))
        },
        serverHeading = { server ->
            // The address as it was typed or found, without the slash the canonical form ends in.
            val shown = server.address.url.removeSuffix("/")
            val cleartext = shown.startsWith("http://")
            val address = if (cleartext) stringResource(R.string.server_detail_cleartext, shown) else shown
            // A server that is not set up is its notice's subject, so the notice leads; the notice itself says which server.
            if (server.setUp) {
                StepHeading(title = server.name, subtitle = address)
            } else {
                StepHeading(title = stringResource(R.string.setup_incomplete_title))
            }
        },
        barActions = { ReportProblemAction() },
        localNetwork =
            enterServer?.takeIf { it.error == SetupError.LocalNetworkDenied }?.let { localNetworkAsk(actions) },
        foundServers = enterServerShown?.let { step -> { DiscoveredServers(step, actions) } },
        errorText = { error -> errorText(error.toSetupError()) },
        signInOptions =
            ConnectSignInOptions(
                // Quick Connect is a button under the form, not a tab: the form is always the password's.
                modeSelector = { _, _ -> },
                aboveFields = { signInShown?.let { PublicUsers(it, actions, passwordFocus) } },
                // After the error, so the error sits directly under the fields it is about.
                afterError =
                    {
                        signInShown?.let {
                            AfterError(it, actions)
                            ForgotPasswordOverlay(it.forgot, actions.forgotPassword)
                        }
                    },
                fieldFocus = mapOf(PASSWORD_FIELD to passwordFocus),
                notSetUpAction = { SetupWizardEntry(actions) },
            ),
    )
}

@Composable
private fun setupCopy() =
    ConnectCopy(
        modeLabel = { _, _ -> stringResource(R.string.setup_password) },
        fieldLabel = { _, _, field ->
            stringResource(if (field.id == USERNAME_FIELD) R.string.setup_username else R.string.setup_password)
        },
    )

/** What the sign-in step's controls do: each edit of the form is the edit of the one field it changed. */
private fun signInActions(
    signIn: UiState.SignIn?,
    actions: Actions,
) = ConnectSignInActions(
    onSelectMode = {},
    onEditForm = { transform ->
        val before =
            SignInForm(
                SetupMode.Password,
                mapOf(
                    USERNAME_FIELD to signIn?.username.orEmpty(),
                    PASSWORD_FIELD to signIn?.password.orEmpty(),
                ),
            )
        before.changesTo(before.transform()).forEach { (field, value) ->
            if (field == USERNAME_FIELD) actions.onUsername(value) else actions.onPassword(value)
        }
    },
    onConnect = actions.onSignIn,
    onStartLink = actions.onQuickConnect,
    onChangeServer = actions.onChangeServer,
    onCheckAgain = actions.onRecheckServer,
)

/** The way to grant the local-network permission: asks for it, and once asked, opens the app's settings where it can be switched on. */
@Composable
private fun localNetworkAsk(actions: Actions) =
    AllowLocalNetwork(
        label = if (actions.localNetworkAsked) R.string.connection_open_settings else R.string.connection_allow_local_network,
        shortLabel = if (actions.localNetworkAsked) R.string.connection_open_settings else R.string.connection_allow_local_network,
        run = actions.onAllowLocalNetwork,
    )

@Composable
private fun SetupWizardEntry(actions: Actions) {
    BingeOutlinedButton(
        label = stringResource(R.string.startup_open),
        onClick = actions.startup.onOpen,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The servers that answered on the local network, each one tap from connecting; a quiet line while the search runs. */
@Composable
private fun ColumnScope.DiscoveredServers(
    state: UiState.EnterServer,
    actions: Actions,
) {
    if (state.discovered.isNotEmpty()) {
        ItemGroup(
            title = stringResource(R.string.setup_found_title),
            rows =
                state.discovered.map { server ->
                    val cleartext = server.address.startsWith("http://")
                    ListItem(
                        icon = if (cleartext) Icons.Outlined.LockOpen else Icons.Outlined.Dns,
                        label = server.name,
                        // The marker leads, so a long address takes the ellipsis, never "Not encrypted".
                        detail = if (cleartext) stringResource(R.string.server_detail_cleartext, server.address) else server.address,
                        supportingMaxLines = ADDRESS_LINES,
                        onClick = { actions.onChooseServer(server) },
                    )
                },
        )
    } else if (state.discovering) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            BingeLoadingIndicator()
            Text(stringResource(R.string.setup_searching), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The accounts the server lists on its login screen, each one tap from the form. */
@Composable
private fun ColumnScope.PublicUsers(
    state: UiState.SignIn,
    actions: Actions,
    passwordFocus: FocusRequester,
) {
    if (state.publicUsers.isEmpty()) return
    ItemGroup(
        title = stringResource(R.string.setup_pick_user),
        rows =
            state.publicUsers.map { user ->
                ListItem(
                    icon = Icons.Outlined.Person,
                    label = user.name,
                    leadingContent = { UserAvatar(user.name, state.publicPictures[user.id]) },
                    selected = user.name == state.username,
                    onClick = {
                        actions.onPickUser(user)
                        if (user.hasPassword) passwordFocus.requestFocus()
                    },
                )
            },
    )
}

/** Under the form and its error: the reset of a forgotten password, the local-network ask, and Quick Connect with what it is for. */
@Composable
private fun ColumnScope.AfterError(
    state: UiState.SignIn,
    actions: Actions,
) {
    BingeTextButton(
        label = stringResource(R.string.forgot_password),
        onClick = { actions.forgotPassword?.start() },
        enabled = state.username.isNotBlank() && !state.busy,
        loading = state.forgot == ForgotPasswordStep.Asking,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.passwordError == SetupError.LocalNetworkDenied || state.quickConnectError == SetupError.LocalNetworkDenied) {
        val ask = localNetworkAsk(actions)
        BingeOutlinedButton(label = stringResource(ask.label), onClick = ask.run, modifier = Modifier.fillMaxWidth())
    }
    if (state.quickConnectOn) {
        BingeOutlinedButton(
            label = stringResource(R.string.setup_quick_connect),
            onClick = actions.onQuickConnect,
            leadingIcon = Icons.Outlined.QrCode2,
            loading = state.gettingCode,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    // An account that exists only through single sign-on has no password here; this is how it gets in. It is said before
    // Quick Connect is tried: once a code is being fetched, or the server has said it is off, or the code has expired, it
    // would only repeat the screen or point at something that isn't there.
    if (state.showsQuickConnectHint) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.setup_quick_connect_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun quickConnectCopy() =
    ConnectLinkCopy(title = stringResource(R.string.quick_connect_title), body = stringResource(R.string.quick_connect_body))

/** The code Quick Connect waits on, as the sheet shows it but without its window, which a frame cannot capture. */
@Composable
internal fun QuickConnectCodeBody(
    code: String,
    actions: Actions,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        ConnectLinkContent(
            link = LinkState(code, openUrl = null, launchPending = false),
            copy = quickConnectCopy(),
            onOpen = {},
            onCancel = actions.onCancelQuickConnect,
        )
    }
}
