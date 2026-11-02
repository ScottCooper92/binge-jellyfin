package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.BackupCreation
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.ServerBackup
import com.binge.designsystem.R as DesR

/** What the backups page can ask of its view model, and of the console. */
data class BackupsActions(
    val onAskCreate: (Boolean) -> Unit = {},
    val onChoose: (BackupOptions) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onDismissCreation: () -> Unit = {},
    val onOpen: (ServerBackup?) -> Unit = {},
    val onAskRestore: (ServerBackup?) -> Unit = {},
    val onRestore: () -> Unit = {},
    val onDismissRestore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's backups, newest first, with New backup as the footer's primary. A backup being made, or the last one's result, and
 * where a restore has got to, show above the list. A backup's own sheet holds its Restore, last, red and behind a confirm.
 * [serverVersion] is the running server's, so a backup made by another release is marked.
 */
@Composable
fun BackupsScreen(
    state: BackupsUiState,
    actions: BackupsActions,
    serverVersion: JellyfinVersion? = null,
    now: Long = System.currentTimeMillis(),
) {
    val ready = state as? BackupsUiState.Ready
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.backups_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        bottomBar = {
            CreateFooter(
                label = stringResource(R.string.backups_new),
                icon = Icons.Outlined.Backup,
                onClick = { actions.onAskCreate(true) },
                visible = state !is BackupsUiState.Failed,
                enabled = ready != null && ready.restore != Restore.Restoring,
                loading = ready?.creating == true,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                BackupsUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                is BackupsUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                is BackupsUiState.Ready -> {
                    Backups(state, actions, inner, now)
                    SnackbarMessage(state.restore.refusal()?.let { stringResource(it) }, host)
                    CreationMessage(state.creation, host, actions.onDismissCreation)
                }
            }
        }
    }
    if (ready != null) BackupsOverlays(ready, actions, serverVersion, now)
}

@Composable
private fun Backups(
    state: BackupsUiState.Ready,
    actions: BackupsActions,
    inner: PaddingValues,
    now: Long,
) {
    val status = statusRows(state, actions)
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item { Text(stringResource(R.string.backups_body), style = MaterialTheme.typography.bodyMedium) }
        status?.let { rows -> item { ItemGroup(title = null, rows = rows) } }
        if (state.backups.isEmpty()) {
            item { ConsoleEmptyScreen(stringResource(R.string.backups_empty), Icons.Outlined.Backup) }
        } else {
            item { ItemGroup(title = null, rows = state.backups.map { it.row(state, actions, now) }) }
        }
    }
}

/** What the page is doing, as rows above the list: a backup being made, a restore going on or done. A backup made or refused is a message over the page, not a row. */
@Composable
private fun statusRows(
    state: BackupsUiState.Ready,
    actions: BackupsActions,
): List<ListItem>? {
    val rows =
        listOfNotNull(
            when (state.creation) {
                BackupCreation.Idle -> null
                BackupCreation.Creating ->
                    ListItem(
                        icon = Icons.Outlined.Backup,
                        label = stringResource(R.string.backups_creating),
                        detail = stringResource(R.string.backups_creating_detail),
                        supportingMaxLines = STATUS_LINES,
                        trailingContent = { BingeLoadingIndicator() },
                        clickable = false,
                    )
                is BackupCreation.Created, is BackupCreation.Failed -> null
            },
            when (state.restore) {
                Restore.Restoring ->
                    ListItem(
                        icon = Icons.Outlined.Backup,
                        label = stringResource(R.string.backups_restoring),
                        detail = stringResource(R.string.backups_restoring_detail),
                        supportingMaxLines = STATUS_LINES,
                        trailingContent = { BingeLoadingIndicator() },
                        clickable = false,
                    )
                Restore.Restored ->
                    ListItem(
                        icon = Icons.Outlined.CheckCircle,
                        label = stringResource(R.string.backups_restored),
                        detail = stringResource(R.string.backups_restored_detail),
                        supportingMaxLines = STATUS_LINES,
                        trailingContent = { DismissButton(actions.onDismissRestore) },
                        clickable = false,
                    )
                Restore.NotBack, Restore.Failed, null -> null
            },
        )
    return rows.ifEmpty { null }
}

/**
 * A backup that was made, as a message over the page, so the list never moves for it. It carries a dismiss action and clears the
 * creation when it ends, by the action or by running out of time. One the server refused is a dialog instead ([BackupNotMadeDialog]):
 * it is a cause and a next step.
 */
@Composable
private fun CreationMessage(
    creation: BackupCreation,
    host: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val message = if (creation is BackupCreation.Created) stringResource(R.string.backups_created_message) else null
    LaunchedEffect(creation) {
        if (message != null) {
            host.showSnackbar(message, withDismissAction = true, duration = SnackbarDuration.Long)
            onDismiss()
        }
    }
}

/** Why a backup was not made: the server's own reason, the free-space hint for a refusal, or the failure's plain sentence. */
@Composable
internal fun BackupCreation.Failed.reason(): String {
    val failure = error.consoleFailure()
    return failure.reason ?: stringResource(if (failure == ConsoleFailure.Refused) R.string.backups_create_refused else failure.message)
}

@Composable
private fun DismissButton(onClick: () -> Unit) = BingeTextButton(label = stringResource(R.string.backups_done), onClick = onClick)

/** What a restore that failed outright says, as a snackbar; one the server did not come back from is a dialog, and null when it is going or done. */
private fun Restore?.refusal(): Int? = if (this == Restore.Failed) R.string.backups_restore_failed else null

@Composable
private fun ServerBackup.row(
    state: BackupsUiState.Ready,
    actions: BackupsActions,
    now: Long,
): ListItem =
    ListItem(
        icon = Icons.Outlined.Backup,
        label = formatRelativeOrAbsolute(createdMillis, now) ?: stringResource(R.string.backups_date_unknown),
        detail = stringResource(R.string.backups_detail, serverVersion, options.summary()),
        supportingMaxLines = STATUS_LINES,
        enabled = state.restore != Restore.Restoring,
        onClick = { actions.onOpen(this) },
    )

/** What the backup holds, in words: "Database, Metadata". */
@Composable
internal fun BackupOptions.summary(): String =
    listOfNotNull(
        stringResource(R.string.backups_database).takeIf { database },
        stringResource(R.string.backups_metadata).takeIf { metadata },
        stringResource(R.string.backups_subtitles).takeIf { subtitles },
        stringResource(R.string.trickplay_title).takeIf { trickplay },
    ).joinToString(stringResource(R.string.backups_list_separator))

private const val LOADING_ROWS = 5
private const val STATUS_LINES = 3
