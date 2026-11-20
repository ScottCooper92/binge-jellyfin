package com.cooper.homelab.ephyra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.hasPaneBeside
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.DiscoveredServer
import com.cooper.homelab.ephyra.server.PublicUser
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import com.cooper.homelab.ephyra.ui.console.UserAvatar
import com.cooper.homelab.ephyra.ui.console.rememberOwnPicture
import com.binge.designsystem.R as DesR

/** What the screens can ask of the [JellyfinViewModel]. */
data class Actions(
    val onAddress: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    /** The user's agreement, or its withdrawal, to plain HTTP to the public host the typed address names. */
    val onAllowCleartext: (Boolean) -> Unit = {},
    val onTryDemo: () -> Unit = {},
    val onUsername: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    /** The forgotten-password steps; null where none is wired (a frame). */
    val forgotPassword: ForgotPasswordFlow? = null,
    val onPickUser: (PublicUser) -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onQuickConnect: () -> Unit = {},
    val onCancelQuickConnect: () -> Unit = {},
    val onChangeServer: () -> Unit = {},
    /** Asks the server again whether its setup is done, from the notice a server that is not set up gets. */
    val onRecheckServer: () -> Unit = {},
    /** The setup wizard of a server that has not finished its first-run setup. */
    val startup: StartupActions = StartupActions(),
    val onRefresh: () -> Unit = {},
    val onAskSignOut: () -> Unit = {},
    val onCancelSignOut: () -> Unit = {},
    val onSignOut: () -> Unit = {},
    /** Asks to take back the user's agreement to plain HTTP to the saved server, which signs them out of it. */
    val onAskTakeBack: () -> Unit = {},
    val onCancelTakeBack: () -> Unit = {},
    val onDiscover: () -> Unit = {},
    val onChooseServer: (DiscoveredServer) -> Unit = {},
    val onOpenPage: (ConsolePage) -> Unit = {},
    /** A page opened on one of its settings, by row key: a search result. */
    val onOpenRow: (ConsolePage, String?) -> Unit = { page, _ -> },
    /** A search result opened beside the home: the search closes and the page takes its place in the detail pane. */
    val onOpenResult: (ConsolePage, String?) -> Unit = { page, _ -> },
    /** Asks for the local-network permission, or, once it has been asked for, opens the app's settings where it can be switched on. */
    val onAllowLocalNetwork: () -> Unit = {},
    /** The permission has been asked for, so the way to it is now the app's settings. */
    val localNetworkAsked: Boolean = false,
    /** The page showing beside the home, marked in its list; null where none is (the pages' own actions carry none). */
    val selectedPage: ConsolePage? = null,
    /** What the user has chosen this app may report, and the two ways to change it. */
    val appSettings: AppSettings = AppSettings(),
    val onShareUsageData: (Boolean) -> Unit = {},
    val onSendCrashReports: (Boolean) -> Unit = {},
    val onClient: (Boolean) -> Unit = {},
    /** An administrator's notification settings; null for anyone else, who is shown no such group. */
    val notifications: NotificationSettings? = null,
    val onNotificationsEnabled: (Boolean) -> Unit = {},
    val onNotificationInterval: (Int) -> Unit = {},
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
    // The address and sign-in steps are the shared connect screen's, with a bar of their own.
    if (state is UiState.EnterServer || state is UiState.SignIn) {
        ConnectSetupScreen(state, actions)
        return
    }
    BingeScreenScaffold(
        title = stringResource(R.string.app_label),
        // An administrator's own settings are one tap away, in the account: avatar, as binge-seerr has it.
        actions = {
            if (state is UiState.SignedIn &&
                state.isAdministrator &&
                state.health == ConnectionHealth.Healthy
            ) {
                AccountAction(state, actions)
            }
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is UiState.Startup -> StartupStep(state, actions, inner)
                is UiState.SignedIn -> SignedIn(state, actions, inner, padding.calculateTopPadding())
                is UiState.EnterServer, is UiState.SignIn -> Unit
            }
        }
    }
}

@Composable
internal fun SetupColumn(
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
private fun SignedIn(
    state: UiState.SignedIn,
    actions: Actions,
    inner: PaddingValues,
    topInset: Dp,
) {
    when {
        // Nothing is shown of a server that has not answered yet: its pages would only fail one by one, and the home is drawn
        // from what was saved the last time. A start that cannot reach the server lands on its problem, not on a console that
        // looks usable until the read times out.
        state.health == ConnectionHealth.Checking ->
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
        // A server that cannot be reached or read takes the place of the home, with its ways out.
        state.health.isProblem -> ConnectionProblem(state, actions, Modifier.padding(inner))
        else -> SignedInHome(state, actions, inner, topInset)
    }
    if (state.confirmingSignOut) SignOutConfirm(actions)
    if (state.confirmingTakeBack) state.consentHost?.let { TakeBackConfirm(it, actions) }
}

/** Who is signed in, and what's playing on the server, under the server card. */
@Composable
internal fun ServerGroup(
    state: UiState.SignedIn,
    actions: Actions,
) {
    val picture = rememberOwnPicture(state)
    ItemGroup(
        title = null,
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
            ) + releaseNotesItems(state.session.serverVersion),
    )
}

@Composable
internal fun LibraryGroup(
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
            SetupError.CleartextRefused -> R.string.error_cleartext_refused
            SetupError.ClientCertificateNeeded -> R.string.error_client_certificate
            SetupError.Generic -> R.string.error_generic
        },
    )
