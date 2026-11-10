package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.binge.designsystem.R as DesR

/** What the server page can ask of its view model, and of the console. */
data class ServerActions(
    val onConfirm: (Boolean) -> Unit = {},
    val onRestart: () -> Unit = {},
    val onConfirmShutdown: (Boolean) -> Unit = {},
    val onShutdown: () -> Unit = {},
    val onDismissRestart: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server itself: its name, version, address and system, marked when an update or a restart is
 * waiting. Restart and Shut down are last, red, and behind a confirm, and how each is going shows above
 * them. A server that can't restart itself says so instead.
 */
@Composable
fun ServerScreen(
    state: ServerUiState,
    actions: ServerActions,
) {
    // Held above the loading, failed and ready branches, so a reload does not drop where the user was.
    val scroll = rememberScrollState()
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.server_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ServerUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is ServerUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is ServerUiState.Ready -> {
                    BingePullToRefresh(
                        isRefreshing = state.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) { Server(state, actions, inner, scroll) }
                    RefusedMessage(state.refreshFailed, host, R.string.console_not_refreshed)
                    // A restart or shutdown the server didn't take is a short message; a restart that never came back is a dialog.
                    SnackbarMessage(state.refusal()?.let { stringResource(it) }, host)
                }
            }
        }
    }
    val ready = state as? ServerUiState.Ready
    if (ready?.confirming == true) RestartDialog(ready.info, actions)
    if (ready?.confirmingShutdown == true) ShutdownDialog(ready.info, actions)
    if (ready?.restart == Restart.NotBack) RestartNotBackDialog(actions)
}

/** A restart the server never came back from: what happened, and where to look. Not a snackbar: it is a cause and a next step. */
@Composable
internal fun RestartNotBackDialog(
    actions: ServerActions,
    inWindow: Boolean = true,
) {
    ConsoleMessage(
        title = stringResource(R.string.server_not_back_title),
        message = stringResource(R.string.server_not_back_body),
        onDismiss = actions.onDismissRestart,
        inWindow = inWindow,
    )
}

/** Shutting the server down, after its name, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun ShutdownDialog(
    info: ServerInfo,
    actions: ServerActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.server_shutdown_title, info.name),
        message = stringResource(R.string.server_shutdown_body),
        confirmLabel = stringResource(R.string.server_shutdown),
        onConfirm = actions.onShutdown,
        onDismissRequest = { actions.onConfirmShutdown(false) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** Restarting the server, after its name, as a confirm. [inWindow] false draws its body for a frame. */
@Composable
internal fun RestartDialog(
    info: ServerInfo,
    actions: ServerActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.server_restart_title, info.name),
        message = stringResource(R.string.server_restart_body),
        confirmLabel = stringResource(R.string.server_restart),
        onConfirm = actions.onRestart,
        onDismissRequest = { actions.onConfirm(false) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** The message of a restart or shutdown the server didn't take; null when none did. */
private fun ServerUiState.Ready.refusal(): Int? =
    when {
        shutdown == Shutdown.Failed -> shutdown.message
        restart == Restart.Failed -> restart.message
        else -> null
    }

@Composable
private fun Server(
    state: ServerUiState.Ready,
    actions: ServerActions,
    inner: PaddingValues,
    scroll: ScrollState,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        ItemGroup(
            title = null,
            rows = state.info.rows(),
            modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
        )
        // Where a restart or shutdown has got to, while it is going on or done. A failure is a message instead.
        if (state.refusal() == null) {
            (state.shutdown?.message ?: state.restart?.takeIf { it != Restart.NotBack }?.message)?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (state.info.canRestart) {
            BingeTextButton(
                label = stringResource(R.string.server_restart),
                onClick = { actions.onConfirm(true) },
                leadingIcon = Icons.Outlined.RestartAlt,
                loading = state.restart == Restart.Restarting,
                enabled = !state.down,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(stringResource(R.string.server_cant_restart), style = MaterialTheme.typography.bodyMedium)
        }
        BingeTextButton(
            label = stringResource(R.string.server_shutdown),
            onClick = { actions.onConfirmShutdown(true) },
            leadingIcon = Icons.Outlined.PowerSettingsNew,
            loading = state.shutdown == Shutdown.ShuttingDown,
            enabled = !state.down && state.restart != Restart.Restarting,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ServerInfo.rows(): List<ListItem> =
    listOfNotNull(
        ListItem(
            icon = Icons.Outlined.Dns,
            label = name.ifBlank { stringResource(R.string.server_unnamed) },
            detail = address,
            clickable = false,
        ),
        ListItem(
            icon = if (updateAvailable) Icons.Outlined.NewReleases else Icons.Outlined.Info,
            label = stringResource(R.string.server_version, version),
            note = if (updateAvailable) ListItemNote(stringResource(R.string.server_update)) else null,
            clickable = false,
        ),
        ListItem(
            icon = Icons.Outlined.Memory,
            label =
                listOf(operatingSystem, architecture)
                    .filter {
                        it.isNotBlank()
                    }.joinToString(" · ")
                    .ifBlank { stringResource(R.string.server_system_unknown) },
            clickable = false,
        ),
        if (restartPending) {
            ListItem(
                icon = Icons.Outlined.RestartAlt,
                label = stringResource(R.string.server_restart_pending),
                detail = stringResource(R.string.server_restart_pending_detail),
                clickable = false,
            )
        } else {
            null
        },
    )

private val Shutdown.message: Int
    get() =
        when (this) {
            Shutdown.ShuttingDown -> R.string.server_shutting_down
            Shutdown.ShutDown -> R.string.server_shut_down
            Shutdown.Failed -> R.string.server_shutdown_failed
        }

private val Restart.message: Int
    get() =
        when (this) {
            Restart.Restarting -> R.string.server_restarting
            Restart.Restarted -> R.string.server_restarted
            Restart.NotBack -> R.string.server_not_back_title
            Restart.Failed -> R.string.server_restart_failed
        }

private const val LOADING_ROWS = 5
