package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.binge.designsystem.R as DesR

/** What the screens can ask of the [JellyfinViewModel]. */
data class Actions(
    val onAddress: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onUsername: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onQuickConnect: () -> Unit = {},
    val onCancelQuickConnect: () -> Unit = {},
    val onChangeServer: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onAskSignOut: () -> Unit = {},
    val onCancelSignOut: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    val onDiscover: () -> Unit = {},
    val onChooseServer: (DiscoveredServer) -> Unit = {},
    val onOpenPage: (ConsolePage) -> Unit = {},
)

/** Lines a server row's marker and address may take: enough for the whole address at 2× font beside Change. */
private const val ADDRESS_LINES = 5

@Composable
fun JellyfinRoot(
    state: UiState,
    actions: Actions,
) {
    BingeScreenScaffold(title = stringResource(R.string.app_label), bar = ScreenBar.Small) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is UiState.EnterServer -> EnterServer(state, actions, inner)
                is UiState.SignIn -> SignIn(state, actions, inner)
                is UiState.SignedIn -> SignedIn(state, actions, inner)
            }
        }
    }
}

@Composable
private fun SetupColumn(
    inner: PaddingValues,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .imePadding(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        content()
    }
}

@Composable
private fun EnterServer(
    state: UiState.EnterServer,
    actions: Actions,
    inner: PaddingValues,
) {
    LaunchedEffect(Unit) { actions.onDiscover() }
    SetupColumn(inner) {
        Text(stringResource(R.string.setup_server_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.setup_server_body), style = MaterialTheme.typography.bodyMedium)
        DiscoveredServers(state, actions)
        OutlinedTextField(
            value = state.address,
            onValueChange = actions.onAddress,
            label = { Text(stringResource(R.string.setup_server_field)) },
            placeholder = { Text("192.168.1.10:8096") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(errorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { actions.onConnect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        BingeFilledButton(
            label = stringResource(R.string.setup_connect),
            onClick = actions.onConnect,
            loading = state.checking,
            enabled = state.address.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The servers that answered on the local network, each one tap from connecting; a quiet line while the search runs. */
@Composable
private fun DiscoveredServers(
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

@Composable
private fun SignIn(
    state: UiState.SignIn,
    actions: Actions,
    inner: PaddingValues,
) {
    SetupColumn(inner) {
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = if (state.cleartext) Icons.Outlined.LockOpen else Icons.Outlined.Dns,
                        label = state.server.serverName.ifBlank { state.baseUrl },
                        detail =
                            if (state.cleartext) {
                                stringResource(R.string.server_detail_cleartext, state.baseUrl)
                            } else {
                                state.baseUrl
                            },
                        // Change sits beside it, so at large fonts the address wraps rather than shrinking to a few characters.
                        supportingMaxLines = ADDRESS_LINES,
                        trailingContent = {
                            BingeTextButton(
                                label = stringResource(R.string.setup_change),
                                onClick = actions.onChangeServer,
                            )
                        },
                        clickable = false,
                    ),
                ),
        )
        val code = state.quickConnectCode
        if (code != null) {
            QuickConnectCode(code, actions)
            return@SetupColumn
        }
        Text(stringResource(R.string.setup_sign_in_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.username,
            onValueChange = actions.onUsername,
            label = { Text(stringResource(R.string.setup_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = actions.onPassword,
            label = { Text(stringResource(R.string.setup_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            // Only a wrong password is the field's fault; any other failure is said beside the button that failed.
            isError = state.passwordError == SetupError.WrongCredentials,
            supportingText = state.passwordError?.takeIf { it == SetupError.WrongCredentials }?.let { { Text(errorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { actions.onSignIn() }),
            modifier = Modifier.fillMaxWidth(),
        )
        BingeFilledButton(
            label = stringResource(R.string.setup_sign_in),
            onClick = actions.onSignIn,
            loading = state.busy,
            enabled = state.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        state.passwordError?.takeUnless { it == SetupError.WrongCredentials }?.let { ErrorLine(it) }
        BingeOutlinedButton(
            label = stringResource(R.string.setup_quick_connect),
            onClick = actions.onQuickConnect,
            leadingIcon = Icons.Outlined.QrCode2,
            loading = state.gettingCode,
            modifier = Modifier.fillMaxWidth(),
        )
        state.quickConnectError?.let { ErrorLine(it) }
    }
}

/** A failure said under the action it belongs to, rather than on a field that isn't at fault. */
@Composable
private fun ErrorLine(error: SetupError) {
    Text(errorText(error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun QuickConnectCode(
    code: String,
    actions: Actions,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
    ) {
        Text(stringResource(R.string.quick_connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            code,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.quick_connect_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) { BingeLoadingIndicator() }
        BingeTextButton(label = stringResource(R.string.quick_connect_cancel), onClick = actions.onCancelQuickConnect)
    }
}

@Composable
private fun SignedIn(
    state: UiState.SignedIn,
    actions: Actions,
    inner: PaddingValues,
) {
    val promoted = LocalFlags.current.bingePromotion
    LazyColumn(
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item { ServerGroup(state, actions) }
        item { AccountGroup(state, actions) }
        if (ConsolePage.entries.any { it.isAdmin && it.available(state.profile, state.account) }) {
            item { AdminGroup(state, actions) }
        }
        // What Binge can find in the library is Binge's to show; without the flag the app names no Binge.
        if (promoted) item { LibraryGroup(state, actions) }
        item {
            BingeTextButton(
                label = stringResource(R.string.settings_sign_out),
                onClick = actions.onAskSignOut,
                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.confirmingSignOut) SignOutConfirm(actions)
}

/**
 * The sign-out confirm. [inWindow] false draws its body alone, without the dialog window a screen
 * frame can't capture, so a `@PreviewTest` can show it.
 */
@Composable
internal fun SignOutConfirm(
    actions: Actions,
    inWindow: Boolean = true,
) {
    val title = stringResource(R.string.sign_out_title)
    val message = stringResource(if (LocalFlags.current.bingePromotion) R.string.sign_out_body else R.string.sign_out_body_plain)
    val confirm = stringResource(R.string.settings_sign_out)
    if (inWindow) {
        BingeConfirmDialog(title, message, confirm, actions.onSignOut, actions.onCancelSignOut, destructive = true)
    } else {
        BingeConfirmDialogContent(title, message, confirm, actions.onSignOut, actions.onCancelSignOut, destructive = true)
    }
}

/**
 * Where and who: the server, marked when it is not encrypted, and the user, marked when an admin.
 * Then what's playing on it.
 */
@Composable
private fun ServerGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    val session = state.session
    ItemGroup(
        title = stringResource(R.string.settings_server),
        rows =
            listOfNotNull(
                ListItem(
                    icon = if (state.cleartext) Icons.Outlined.LockOpen else Icons.Outlined.Dns,
                    label = session.serverName.ifBlank { session.baseUrl },
                    detail =
                        if (state.cleartext) {
                            stringResource(R.string.server_detail_cleartext, session.baseUrl)
                        } else {
                            stringResource(R.string.server_detail, session.baseUrl, session.serverVersion)
                        },
                    supportingMaxLines = ADDRESS_LINES,
                    clickable = false,
                ),
                ListItem(
                    icon = Icons.Outlined.Person,
                    label = session.userName,
                    detail =
                        stringResource(
                            if (state.isAdministrator) R.string.settings_signed_in_admin else R.string.settings_signed_in_as,
                        ),
                    clickable = false,
                ),
                ListItem(
                    icon = Icons.Outlined.PlayCircle,
                    label = stringResource(R.string.now_playing_title),
                    onClick = { actions.onOpenPage(ConsolePage.NowPlaying) },
                ).takeIf { ConsolePage.NowPlaying.available(state.profile, state.account) },
            ),
    )
}

/** The console's pages for an administrator, shown only to one: the server would refuse anyone else. */
@Composable
private fun AdminGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    ItemGroup(
        title = stringResource(R.string.console_admin_title),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Folder,
                    label = stringResource(R.string.libraries_title),
                    detail = stringResource(R.string.console_libraries_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Libraries) },
                ).takeIf { ConsolePage.Libraries.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Schedule,
                    label = stringResource(R.string.tasks_title),
                    detail = stringResource(R.string.console_tasks_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Tasks) },
                ).takeIf { ConsolePage.Tasks.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.History,
                    label = stringResource(R.string.activity_title),
                    detail = stringResource(R.string.console_activity_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Activity) },
                ).takeIf { ConsolePage.Activity.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Key,
                    label = stringResource(R.string.api_keys_title),
                    detail = stringResource(R.string.console_api_keys_detail),
                    onClick = { actions.onOpenPage(ConsolePage.ApiKeys) },
                ).takeIf { ConsolePage.ApiKeys.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Devices,
                    label = stringResource(R.string.devices_title),
                    detail = stringResource(R.string.console_devices_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Devices) },
                ).takeIf { ConsolePage.Devices.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Group,
                    label = stringResource(R.string.users_title),
                    detail = stringResource(R.string.console_users_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Users) },
                ).takeIf { ConsolePage.Users.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Tune,
                    label = stringResource(R.string.server_settings_title),
                    detail = stringResource(R.string.console_server_settings_detail),
                    onClick = { actions.onOpenPage(ConsolePage.ServerSettings) },
                ).takeIf { ConsolePage.ServerSettings.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.SettingsApplications,
                    label = stringResource(R.string.server_title),
                    detail = stringResource(R.string.console_server_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Server) },
                ).takeIf { ConsolePage.Server.available(state.profile, state.account) },
            ),
    )
}

/** The console's pages for everyone. */
@Composable
private fun AccountGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    ItemGroup(
        title = stringResource(R.string.console_account_title),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.QrCode2,
                    label = stringResource(R.string.approve_title),
                    detail = stringResource(R.string.console_approve_detail),
                    onClick = { actions.onOpenPage(ConsolePage.ApproveCode) },
                ).takeIf { ConsolePage.ApproveCode.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Translate,
                    label = stringResource(R.string.playback_title),
                    detail = stringResource(R.string.console_playback_detail),
                    onClick = { actions.onOpenPage(ConsolePage.Playback) },
                ).takeIf { ConsolePage.Playback.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Dashboard,
                    label = stringResource(R.string.home_layout_title),
                    detail = stringResource(R.string.console_home_layout_detail),
                    onClick = { actions.onOpenPage(ConsolePage.HomeLayout) },
                ).takeIf { ConsolePage.HomeLayout.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.Password,
                    label = stringResource(R.string.password_title),
                    onClick = { actions.onOpenPage(ConsolePage.Password) },
                ).takeIf { ConsolePage.Password.available(state.profile, state.account) },
            ),
    )
}

@Composable
private fun LibraryGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    ItemGroup(
        title = stringResource(R.string.settings_library),
        rows =
            listOf(
                ListItem(
                    icon = Icons.Outlined.VideoLibrary,
                    label = stringResource(R.string.settings_titles),
                    detail =
                        when {
                            state.refreshFailed -> stringResource(R.string.settings_titles_failed)
                            state.titles == null -> stringResource(R.string.settings_titles_checking)
                            else -> null
                        },
                    trailingContent =
                        state.titles?.let { count -> { Text(count.toString(), style = MaterialTheme.typography.bodyLarge) } },
                    clickable = false,
                ),
                ListItem(
                    icon = Icons.Outlined.Refresh,
                    label = stringResource(R.string.settings_refresh),
                    trailingContent = if (state.refreshing) ({ BingeLoadingIndicator() }) else null,
                    onClick = actions.onRefresh,
                ),
            ),
    )
}

@Composable
internal fun errorText(error: SetupError): String =
    stringResource(
        when (error) {
            SetupError.InvalidAddress -> R.string.error_invalid_address
            SetupError.Unreachable -> R.string.error_unreachable
            SetupError.NotJellyfin -> R.string.error_not_jellyfin
            SetupError.WrongCredentials -> R.string.error_wrong_credentials
            SetupError.QuickConnectOff -> R.string.error_quick_connect_off
            SetupError.QuickConnectExpired -> R.string.error_quick_connect_expired
            SetupError.Generic -> R.string.error_generic
        },
    )
