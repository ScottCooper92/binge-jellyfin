package com.cooper.binge.companion.jellyfin.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SettingsApplications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.hasPaneBeside
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleTextField
import com.cooper.binge.companion.jellyfin.ui.console.UserAvatar
import com.cooper.binge.companion.jellyfin.ui.console.rememberOwnPicture
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
    /** Asks for the local-network permission, or, once it has been asked for, opens the app's settings where it can be switched on. */
    val onAllowLocalNetwork: () -> Unit = {},
    /** The permission has been asked for, so the way to it is now the app's settings. */
    val localNetworkAsked: Boolean = false,
    /** The page showing beside the home, marked in its list; null where none is (the pages' own actions carry none). */
    val selectedPage: ConsolePage? = null,
)

/** Whether [page] is the one open beside this list: marked only where a pane is beside it, so never on a narrow window. */
@Composable
internal fun Actions.isOpenBeside(page: ConsolePage): Boolean = hasPaneBeside() && selectedPage == page

/** Lines a server row's marker and address may take: enough for the whole address at 2× font beside Change. */
private const val ADDRESS_LINES = 5

@Composable
fun JellyfinRoot(
    state: UiState,
    actions: Actions,
) {
    BingeScreenScaffold(
        title = stringResource(R.string.app_label),
        bar = ScreenBar.Small,
        // An administrator's own settings are one tap away, in the account: avatar, as binge-seerr has it.
        actions = { if (state is UiState.SignedIn && state.isAdministrator && !state.health.isProblem) AccountAction(state, actions) },
    ) { padding ->
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
        ConsoleTextField(
            value = state.address,
            onValueChange = actions.onAddress,
            label = stringResource(R.string.setup_server_field),
            placeholder = "192.168.1.10:8096",
            isError = state.error != null,
            supporting = state.error?.let { errorText(it) },
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            onDone = actions.onConnect,
        )
        if (state.error == SetupError.LocalNetworkDenied) AllowLocalNetworkButton(actions)
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
        ConsoleTextField(
            value = state.username,
            onValueChange = actions.onUsername,
            label = stringResource(R.string.setup_username),
            imeAction = ImeAction.Next,
        )
        ConsoleTextField(
            value = state.password,
            onValueChange = actions.onPassword,
            label = stringResource(R.string.setup_password),
            secret = true,
            // Only a wrong password is the field's fault; any other failure is said beside the button that failed.
            isError = state.passwordError == SetupError.WrongCredentials,
            supporting = state.passwordError?.takeIf { it == SetupError.WrongCredentials }?.let { errorText(it) },
            imeAction = ImeAction.Go,
            onDone = actions.onSignIn,
        )
        BingeFilledButton(
            label = stringResource(R.string.setup_sign_in),
            onClick = actions.onSignIn,
            loading = state.busy,
            enabled = state.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        state.passwordError?.takeUnless { it == SetupError.WrongCredentials }?.let { ErrorLine(it) }
        if (state.passwordError == SetupError.LocalNetworkDenied || state.quickConnectError == SetupError.LocalNetworkDenied) {
            AllowLocalNetworkButton(actions)
        }
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

/** The way to grant the local-network permission: asks for it, and once asked, opens the app's settings where it can be switched on. */
@Composable
private fun AllowLocalNetworkButton(actions: Actions) {
    BingeOutlinedButton(
        label =
            stringResource(
                if (actions.localNetworkAsked) R.string.connection_open_settings else R.string.connection_allow_local_network,
            ),
        onClick = actions.onAllowLocalNetwork,
        modifier = Modifier.fillMaxWidth(),
    )
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
    // A server that cannot be reached or read takes the place of the home, with its ways out.
    if (state.health.isProblem) {
        ConnectionProblem(state, actions, Modifier.padding(inner))
        if (state.confirmingSignOut) SignOutConfirm(actions)
        return
    }
    LazyColumn(
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        item { ServerHomeCard(state) }
        if ((state.card.tmdb?.withoutTmdbId ?: 0) > 0) item { TmdbCoverageNote(state.card.tmdb) }
        item { ServerGroup(state, actions) }
        // An administrator's own settings are in the account, behind the avatar. Everyone else has nothing else to
        // show, so their home is their account: the same rows, inline, and Sign out last.
        if (!state.isAdministrator) item { AccountGroup(state, actions) }
        if (ConsolePage.entries.any { it.isAdmin && it.available(state.profile, state.account) }) {
            item { AdminGroup(state, actions) }
        }
        // What Binge can find in the library is Binge's to show; without the flag the app names no Binge.
        if (promoted) item { LibraryGroup(state, actions) }
        if (!state.isAdministrator) item { SignOutButton(actions) }
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

/** Who is signed in, and what's playing on the server, under the server card. */
@Composable
private fun ServerGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    val picture = rememberOwnPicture(state)
    ItemGroup(
        title = stringResource(R.string.settings_server),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Person,
                    // Their picture where they have one; the person icon where they have none.
                    leadingContent = picture?.let { bitmap -> { UserAvatar(state.session.userName, bitmap) } },
                    label = state.session.userName,
                    detail =
                        stringResource(
                            if (state.isAdministrator) R.string.settings_signed_in_admin else R.string.settings_signed_in_as,
                        ),
                    clickable = false,
                ),
                ListItem(
                    icon = Icons.Outlined.PlayCircle,
                    label = stringResource(R.string.now_playing_title),
                    selected = actions.isOpenBeside(ConsolePage.NowPlaying),
                    onClick = { actions.onOpenPage(ConsolePage.NowPlaying) },
                ).takeIf { ConsolePage.NowPlaying.available(state.profile, state.account) },
                ListItem(
                    icon = Icons.Outlined.LiveTv,
                    label = stringResource(R.string.live_tv_title),
                    detail = stringResource(R.string.console_live_tv_detail),
                    selected = actions.isOpenBeside(ConsolePage.LiveTv),
                    onClick = { actions.onOpenPage(ConsolePage.LiveTv) },
                ).takeIf { ConsolePage.LiveTv.available(state.profile, state.account) },
            ),
    )
}

/** One of the administrator's pages as the home lists it: its icon, name and the line under it. */
private class AdminPage(
    val page: ConsolePage,
    val icon: ImageVector,
    @StringRes val label: Int,
    @StringRes val detail: Int,
)

private val adminPages =
    listOf(
        AdminPage(ConsolePage.Libraries, Icons.Outlined.Folder, R.string.libraries_title, R.string.console_libraries_detail),
        AdminPage(ConsolePage.Tasks, Icons.Outlined.Schedule, R.string.tasks_title, R.string.console_tasks_detail),
        AdminPage(ConsolePage.Activity, Icons.Outlined.History, R.string.activity_title, R.string.console_activity_detail),
        AdminPage(ConsolePage.Logs, Icons.Outlined.Description, R.string.logs_title, R.string.console_logs_detail),
        AdminPage(ConsolePage.ApiKeys, Icons.Outlined.Key, R.string.api_keys_title, R.string.console_api_keys_detail),
        AdminPage(ConsolePage.Devices, Icons.Outlined.Devices, R.string.devices_title, R.string.console_devices_detail),
        AdminPage(ConsolePage.Users, Icons.Outlined.Group, R.string.users_title, R.string.console_users_detail),
        AdminPage(ConsolePage.ServerSettings, Icons.Outlined.Tune, R.string.server_settings_title, R.string.console_server_settings_detail),
        AdminPage(ConsolePage.Network, Icons.Outlined.Router, R.string.network_title, R.string.console_network_detail),
        AdminPage(ConsolePage.Advanced, Icons.Outlined.Build, R.string.advanced_title, R.string.console_advanced_detail),
        AdminPage(ConsolePage.Trickplay, Icons.Outlined.Image, R.string.trickplay_title, R.string.console_trickplay_detail),
        AdminPage(ConsolePage.LibraryDisplay, Icons.Outlined.ViewModule, R.string.display_title, R.string.console_display_detail),
        AdminPage(ConsolePage.MetadataDefaults, Icons.Outlined.Category, R.string.metadata_title, R.string.console_metadata_detail),
        AdminPage(ConsolePage.Plugins, Icons.Outlined.Extension, R.string.plugins_title, R.string.console_plugins_detail),
        AdminPage(ConsolePage.Transcoding, Icons.Outlined.Memory, R.string.transcoding_title, R.string.console_transcoding_detail),
        AdminPage(ConsolePage.Backups, Icons.Outlined.Backup, R.string.backups_title, R.string.console_backups_detail),
        AdminPage(ConsolePage.Server, Icons.Outlined.SettingsApplications, R.string.server_title, R.string.console_server_detail),
    )

/** The console's pages for an administrator, shown only to one: the server would refuse anyone else. */
@Composable
private fun AdminGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    ItemGroup(
        title = stringResource(R.string.console_admin_title),
        rows =
            adminPages.filter { it.page.available(state.profile, state.account) }.map { entry ->
                ListItem(
                    icon = entry.icon,
                    label = stringResource(entry.label),
                    detail = stringResource(entry.detail),
                    selected = actions.isOpenBeside(entry.page),
                    onClick = { actions.onOpenPage(entry.page) },
                )
            },
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
            SetupError.LocalNetworkDenied -> R.string.error_local_network
            SetupError.NotJellyfin -> R.string.error_not_jellyfin
            SetupError.WrongCredentials -> R.string.error_wrong_credentials
            SetupError.QuickConnectOff -> R.string.error_quick_connect_off
            SetupError.QuickConnectExpired -> R.string.error_quick_connect_expired
            SetupError.Generic -> R.string.error_generic
        },
    )
