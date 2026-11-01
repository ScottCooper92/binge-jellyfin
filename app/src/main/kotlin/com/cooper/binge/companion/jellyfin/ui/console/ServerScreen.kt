package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemNote
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
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines the restart's progress takes, so it never moves the button. */
private const val STATUS_LINES = 2

/**
 * The server itself: its name, version, address and system, marked when an update or a restart is
 * waiting. Restart is last, red, and behind a confirm, and how it's going shows above it. A server
 * that can't restart itself says so instead.
 */
@Composable
fun ServerScreen(
    state: ServerUiState,
    actions: ServerActions,
) {
    BingeScreenScaffold(title = stringResource(R.string.server_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                ServerUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BingeLoadingIndicator() }
                is ServerUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is ServerUiState.Ready -> Server(state, actions, inner)
            }
        }
    }
    val ready = state as? ServerUiState.Ready
    if (ready?.confirming == true) RestartDialog(ready.info, actions)
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

@Composable
private fun Server(
    state: ServerUiState.Ready,
    actions: ServerActions,
    inner: PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inner),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        ItemGroup(title = null, rows = state.info.rows())
        Text(
            text = state.restart?.let { stringResource(it.message) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (state.restart == Restart.Failed ||
                    state.restart == Restart.NotBack
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            minLines = STATUS_LINES,
            maxLines = STATUS_LINES,
        )
        if (state.info.canRestart) {
            BingeTextButton(
                label = stringResource(R.string.server_restart),
                onClick = { actions.onConfirm(true) },
                leadingIcon = Icons.Outlined.RestartAlt,
                loading = state.restart == Restart.Restarting,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(stringResource(R.string.server_cant_restart), style = MaterialTheme.typography.bodyMedium)
        }
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

private val Restart.message: Int
    get() =
        when (this) {
            Restart.Restarting -> R.string.server_restarting
            Restart.Restarted -> R.string.server_restarted
            Restart.NotBack -> R.string.server_not_back
            Restart.Failed -> R.string.server_restart_failed
        }
