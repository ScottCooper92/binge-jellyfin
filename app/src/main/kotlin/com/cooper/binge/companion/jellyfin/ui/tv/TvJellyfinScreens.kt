package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.component.TvTextField
import com.binge.designsystem.tv.template.TvListPaneBoard
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.binge.designsystem.tv.template.TvTwoPaneCopy
import com.binge.designsystem.tv.template.TvTwoPanePage
import com.binge.designsystem.tv.template.TvTwoPaneStyle
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.ConnectionAutoRetry
import com.cooper.binge.companion.jellyfin.ui.ConnectionHealth
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.connectionBody
import com.cooper.binge.companion.jellyfin.ui.connectionHeadline
import com.cooper.binge.companion.jellyfin.ui.errorText
import com.binge.designsystem.tv.R as TvR

/** Lines a status under a TV form always takes, so a longer message never moves what is above it. */
private const val STATUS_LINES = 2

/**
 * The television's screens: the same three steps as the phone's, as two-pane TV pages. Focus lands
 * on the step's first control, and a control the remote is on never moves when something below it
 * arrives (servers found on the network) or a step's state changes (a code, an error).
 */
@Composable
fun TvJellyfinRoot(
    state: UiState,
    actions: Actions,
    modifier: Modifier = Modifier,
    playback: TvPlayback? = null,
    describing: String = ACCOUNT_KEY,
) {
    when (state) {
        // TvMessagePage draws no spinner: loading only holds back its actions, so the body is what shows.
        UiState.Loading -> TvMessagePage(body = stringResource(R.string.tv_loading), loading = true, modifier = modifier)
        is UiState.EnterServer -> TvServerPage(state, actions, modifier)
        is UiState.SignIn -> TvSignInPage(state, actions, modifier)
        is UiState.SignedIn -> TvSignedInPage(state, actions, playback, describing, modifier)
    }
}

/**
 * The server step: the address first, where the remote lands, then the servers that answered on the
 * network, filled in below it as they arrive. Choosing one connects to it.
 */
@Composable
private fun TvServerPage(
    state: UiState.EnterServer,
    actions: Actions,
    modifier: Modifier,
) {
    LaunchedEffect(Unit) { actions.onDiscover() }
    val entry = remember { FocusRequester() }
    // Arriving from another step, the control the remote was on is gone with it: land on the address.
    LaunchedEffect(Unit) { entry.requestFocus() }
    // Top-aligned, so the servers that arrive below the address never move the field the remote is on.
    TvTwoPanePage(
        title = stringResource(R.string.app_label),
        style = TvTwoPaneStyle.Board,
        entry = entry,
        modifier = modifier,
        copy = { TvTwoPaneCopy(headline = stringResource(R.string.setup_server_title), body = stringResource(R.string.setup_server_body)) },
        action = {
            TvTextField(
                value = state.address,
                onValueChange = actions.onAddress,
                label = stringResource(R.string.setup_server_field),
                placeholder = "192.168.1.10:8096",
                keyboardType = KeyboardType.Uri,
                autoCorrect = false,
                imeAction = ImeAction.Go,
                onImeAction = actions.onConnect,
                enabled = !state.checking,
                fillWidth = true,
                modifier = Modifier.focusRequester(entry),
            )
            TvStatusLine(state.error?.let { errorText(it) }, isError = true, lines = 1, capped = false)
            TvButton(
                label = stringResource(R.string.setup_connect),
                onClick = actions.onConnect,
                style = TvButtonStyle.Primary,
                // Never disabled on TV: a disabled button the remote can still land on reads as broken. An empty
                // address answers with its error instead.
                loading = state.checking,
            )
            TvFoundServers(state, actions)
        },
    )
}

@Composable
private fun ColumnScope.TvFoundServers(
    state: UiState.EnterServer,
    actions: Actions,
) {
    if (state.discovered.isEmpty() && !state.discovering) return
    Text(
        text = stringResource(R.string.setup_found_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.discovered.isEmpty()) {
        Text(
            text = stringResource(R.string.setup_searching),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    state.discovered.forEach { server ->
        val cleartext = server.address.startsWith("http://")
        TvOptionRow(
            label = server.name,
            supportingText = if (cleartext) stringResource(R.string.server_detail_cleartext, server.address) else server.address,
            // The address is what the user judges a discovered server by, so it wraps rather than shrinking at large fonts.
            supportingLines = ADDRESS_LINES,
            icon = if (cleartext) Icons.Outlined.LockOpen else Icons.Outlined.Dns,
            selected = false,
            onClick = { actions.onChooseServer(server) },
        )
    }
}

/**
 * Sign-in, with Quick Connect first: a code the user approves from a Jellyfin app they are already
 * signed in to, since typing a password with a remote is the worst part of a TV setup. A password
 * is one press away, and back. The "use a password" button is there in every state of the code,
 * loading, shown or failed, so the remote can always rest on it. Back goes the other way: from the
 * password to the code, and from the code to the server step.
 */
@Composable
internal fun TvSignInPage(
    state: UiState.SignIn,
    actions: Actions,
    modifier: Modifier = Modifier,
    startWithPassword: Boolean = false,
) {
    var usePassword by rememberSaveable(state.baseUrl) { mutableStateOf(startWithPassword) }
    // Back steps back, as the remote expects: from the password to the code, and from the code to the server step (which
    // cancels the code). It does not leave the app from the middle of setting it up.
    BackHandler { if (usePassword) usePassword = false else actions.onChangeServer() }
    val entry = remember { FocusRequester() }
    // Moving between the code and the password moves the remote to the new panel's first control, once.
    LaunchedEffect(usePassword) { entry.requestFocus() }
    // Top-aligned, so a code arriving or failing never moves the controls the remote is on.
    TvTwoPanePage(
        title = stringResource(R.string.app_label),
        style = TvTwoPaneStyle.Board,
        entry = entry,
        modifier = modifier,
        copy = {
            TvTwoPaneCopy(
                headline = stringResource(R.string.tv_sign_in_to, state.server.serverName.ifBlank { state.baseUrl }),
                body =
                    if (state.cleartext) {
                        stringResource(R.string.server_detail_cleartext, state.baseUrl)
                    } else {
                        state.baseUrl
                    },
            )
        },
        action = {
            if (usePassword) {
                TvPasswordPanel(
                    state = state,
                    actions = actions,
                    entry = entry,
                    onUseQuickConnect = { usePassword = false },
                )
            } else {
                TvQuickConnectPanel(
                    state = state,
                    actions = actions,
                    entry = entry,
                    onUsePassword = {
                        actions.onCancelQuickConnect()
                        usePassword = true
                    },
                )
            }
            TvButton(label = stringResource(R.string.tv_change_server), onClick = actions.onChangeServer, style = TvButtonStyle.Text)
        },
    )
}

@Composable
private fun ColumnScope.TvQuickConnectPanel(
    state: UiState.SignIn,
    actions: Actions,
    entry: FocusRequester,
    onUsePassword: () -> Unit,
) {
    val code = state.quickConnectCode
    val error = state.quickConnectError
    val failed = error != null && code == null
    // A code is asked for when the panel shows; a failure waits for Try again rather than asking in a loop.
    LaunchedEffect(Unit) { if (code == null && error == null) actions.onQuickConnect() }
    Text(
        text = code ?: " ",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    TvStatusLine(
        text =
            when {
                failed -> errorText(error)
                code != null -> stringResource(R.string.quick_connect_body)
                else -> stringResource(R.string.tv_quick_connect_getting)
            },
        isError = failed,
        // The instruction is how the user approves the code: it is shown whole, never clipped.
        capped = failed || code == null,
    )
    // A failed code collapses to its message and Try again, and the remote moves there: the way on.
    // When the code arrives Try again leaves, and the remote returns to the control that stays.
    val retry = remember { FocusRequester() }
    LaunchedEffect(failed) { if (failed) retry.requestFocus() else entry.requestFocus() }
    if (failed) {
        TvButton(
            label = stringResource(R.string.tv_try_again),
            onClick = actions.onQuickConnect,
            leadingIcon = Icons.Outlined.QrCode2,
            modifier = Modifier.focusRequester(retry),
        )
    }
    TvButton(
        label = stringResource(R.string.tv_use_password),
        onClick = onUsePassword,
        leadingIcon = Icons.Outlined.Password,
        modifier = Modifier.focusRequester(entry),
    )
}

@Composable
private fun ColumnScope.TvPasswordPanel(
    state: UiState.SignIn,
    actions: Actions,
    entry: FocusRequester,
    onUseQuickConnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(TvR.dimen.tv_two_pane_action_gap))) {
        TvTextField(
            value = state.username,
            onValueChange = actions.onUsername,
            label = stringResource(R.string.setup_username),
            autoCorrect = false,
            imeAction = ImeAction.Next,
            enabled = !state.busy,
            fillWidth = true,
            modifier = Modifier.focusRequester(entry),
        )
        TvTextField(
            value = state.password,
            onValueChange = actions.onPassword,
            label = stringResource(R.string.setup_password),
            secret = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Go,
            onImeAction = actions.onSignIn,
            enabled = !state.busy,
            fillWidth = true,
        )
    }
    TvStatusLine(state.passwordError?.let { errorText(it) }, isError = true)
    TvButton(
        label = stringResource(R.string.setup_sign_in),
        onClick = actions.onSignIn,
        style = TvButtonStyle.Primary,
        // Never disabled on TV, as Connect: a disabled button the remote can land on reads as broken.
        loading = state.busy,
    )
    TvButton(label = stringResource(R.string.setup_quick_connect), onClick = onUseQuickConnect, leadingIcon = Icons.Outlined.QrCode2)
}

/**
 * Signed in, as the design system's list-and-pane board: the account's rows (the server and who is signed in, checking the
 * library again, and Sign out, last and behind a confirm whose remote starts on Cancel), then the user's own playback settings
 * when they may change them. A row names itself in the list and holds what it means and its choices in the pane, which is the
 * only place anything changes. [playback] is null on a page that has none to show.
 */
@Composable
private fun TvSignedInPage(
    state: UiState.SignedIn,
    actions: Actions,
    playback: TvPlayback?,
    describing: String,
    modifier: Modifier,
) {
    val promoted = LocalFlags.current.bingePromotion
    var focused by rememberSaveable { mutableStateOf(describing) }
    val entry = remember { FocusRequester() }
    LaunchedEffect(Unit) { entry.requestFocus() }
    val titles = state.titles
    // A server that cannot be reached or read: the board offers its ways out instead of the user's settings.
    val problem = state.health.isProblem
    if (problem) ConnectionAutoRetry(state.health, state.refreshing, actions.onRefresh)
    val accountRows =
        serverRows(state, actions) +
            listOfNotNull(
                // What Binge can find in the library is Binge's to show; without the flag the page is the account alone.
                TvPaneRow(
                    key = REFRESH_KEY,
                    label = stringResource(R.string.settings_refresh),
                    detail =
                        when {
                            state.refreshFailed -> stringResource(R.string.settings_titles_failed)
                            titles == null -> stringResource(R.string.settings_titles_checking)
                            else -> pluralStringResource(R.plurals.tv_titles_count, titles, titles)
                        },
                    icon = Icons.Outlined.Refresh,
                    loading = state.refreshing,
                    options =
                        listOf(
                            TvPaneOption(
                                label = stringResource(R.string.settings_refresh),
                                action = true,
                                onSelect = actions.onRefresh,
                                icon = Icons.Outlined.Refresh,
                            ),
                        ),
                ).takeIf { promoted && !problem },
                TvPaneRow(
                    key = SIGN_OUT_KEY,
                    label = stringResource(R.string.settings_sign_out),
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    options =
                        listOf(
                            TvPaneOption(
                                label = stringResource(R.string.settings_sign_out),
                                action = true,
                                onSelect = actions.onAskSignOut,
                                icon = Icons.AutoMirrored.Outlined.Logout,
                            ),
                        ),
                ),
            )
    val groups =
        listOfNotNull(
            TvPaneGroup(title = stringResource(R.string.settings_signed_in_as), rows = accountRows),
            playback?.takeIf { !problem }?.let { playbackGroup(it) },
        )
    TvListPaneBoard(
        title = stringResource(R.string.app_label),
        groups = groups,
        focusedKey = focused.takeIf { key -> groups.any { group -> group.rows.any { it.key == key } } } ?: ACCOUNT_KEY,
        onFocusRow = { focused = it },
        modifier = modifier.focusRequester(entry).focusGroup(),
    )
    if (state.confirmingSignOut) {
        TvConfirmDialog(
            title = stringResource(R.string.sign_out_title),
            message = stringResource(if (promoted) R.string.sign_out_body else R.string.sign_out_body_plain),
            confirmLabel = stringResource(R.string.settings_sign_out),
            onConfirm = actions.onSignOut,
            onDismissRequest = actions.onCancelSignOut,
            destructive = true,
        )
    }
}

/** The server's row, and, while it cannot be reached or read, the rows to try again and to edit its details. */
@Composable
private fun serverRows(
    state: UiState.SignedIn,
    actions: Actions,
): List<TvPaneRow> {
    val session = state.session
    val server =
        TvPaneRow(
            key = ACCOUNT_KEY,
            label = session.serverName.ifBlank { session.baseUrl },
            body =
                if (state.health.isProblem) {
                    stringResource(connectionHeadline(state.health))
                } else {
                    stringResource(R.string.tv_signed_in_as, session.userName)
                },
            note =
                if (state.health.isProblem) {
                    stringResource(connectionBody(state.health))
                } else if (state.cleartext) {
                    stringResource(R.string.server_detail_cleartext, session.baseUrl)
                } else {
                    stringResource(R.string.server_detail, session.baseUrl, session.serverVersion)
                },
            icon = Icons.Outlined.Dns,
        )
    return listOf(server) + connectionRows(state, actions)
}

/** A row of the board that is one button: its label is the button's, and pressing it does [onSelect]. */
private fun tvActionRow(
    key: String,
    label: String,
    icon: ImageVector,
    onSelect: () -> Unit,
    loading: Boolean = false,
) = TvPaneRow(
    key = key,
    label = label,
    icon = icon,
    loading = loading,
    options = listOf(TvPaneOption(label = label, action = true, onSelect = onSelect, icon = icon)),
)

/** What a saved server that cannot be reached or read offers beside its row: trying again and editing its details. Nothing while it is fine. */
@Composable
private fun connectionRows(
    state: UiState.SignedIn,
    actions: Actions,
): List<TvPaneRow> {
    if (!state.health.isProblem) return emptyList()
    return listOf(
        if (state.health == ConnectionHealth.LocalNetworkDenied) {
            val label =
                stringResource(
                    if (actions.localNetworkAsked) R.string.connection_open_settings else R.string.connection_allow_local_network_short,
                )
            tvActionRow(RETRY_KEY, label, Icons.Outlined.Router, actions.onAllowLocalNetwork)
        } else {
            tvActionRow(RETRY_KEY, stringResource(R.string.connection_retry), Icons.Outlined.Refresh, actions.onRefresh, state.refreshing)
        },
        tvActionRow(EDIT_SERVER_KEY, stringResource(R.string.connection_edit_server), Icons.Outlined.Dns, actions.onChangeServer),
    )
}

internal const val ACCOUNT_KEY = "account"
private const val REFRESH_KEY = "refresh"
private const val RETRY_KEY = "retry"
private const val EDIT_SERVER_KEY = "editServer"
private const val SIGN_OUT_KEY = "signOut"

/** A status under a form, [lines] tall at least, and at most unless not [capped], so it rarely moves the controls below it. */
@Composable
private fun TvStatusLine(
    text: String?,
    isError: Boolean,
    lines: Int = STATUS_LINES,
    capped: Boolean = true,
) {
    Text(
        text = text.orEmpty(),
        style = MaterialTheme.typography.bodyLarge,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = lines,
        maxLines = if (capped) lines else Int.MAX_VALUE,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Lines a found server's address may take, so it stays readable at large font sizes. */
private const val ADDRESS_LINES = 2
