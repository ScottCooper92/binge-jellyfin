package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.BackupOptions
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.ServerBackup
import com.binge.designsystem.R as DesR

/** The sheets and the confirm over the backups page, for whichever is open. */
@Composable
internal fun BackupsOverlays(
    state: BackupsUiState.Ready,
    actions: BackupsActions,
    serverVersion: JellyfinVersion?,
    now: Long,
) {
    state.choosing?.let { CreateBackupSheet(it, actions) }
    state.open?.let { BackupSheet(it, serverVersion, actions, now) }
    state.confirmingRestore?.let { RestoreDialog(it, serverVersion, actions) }
}

/** What the new backup holds: the database, and optional extras that make it larger. The create runs on the server, so it closes at once. */
@Composable
internal fun CreateBackupSheet(
    options: BackupOptions,
    actions: BackupsActions,
    inWindow: Boolean = true,
) {
    ConsoleSheet(onDismissRequest = { actions.onAskCreate(false) }, inWindow = inWindow) {
        ConsoleFieldsForm(
            title = stringResource(R.string.backups_create_title),
            body = stringResource(R.string.backups_create_body),
            submitLabel = stringResource(R.string.backups_create_action),
            onSubmit = actions.onCreate,
            onCancel = { actions.onAskCreate(false) },
        ) {
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.Inventory2,
                            label = stringResource(R.string.backups_database),
                            detail = stringResource(R.string.backups_database_detail),
                            supportingMaxLines = DETAIL_LINES,
                            toggled = options.database,
                            onClick = { actions.onChoose(options.copy(database = !options.database)) },
                        ),
                        ListItem(
                            icon = Icons.Outlined.Description,
                            label = stringResource(R.string.backups_metadata),
                            detail = stringResource(R.string.backups_metadata_detail),
                            supportingMaxLines = DETAIL_LINES,
                            toggled = options.metadata,
                            onClick = { actions.onChoose(options.copy(metadata = !options.metadata)) },
                        ),
                        ListItem(
                            icon = Icons.Outlined.Description,
                            label = stringResource(R.string.backups_subtitles),
                            detail = stringResource(R.string.backups_subtitles_detail),
                            supportingMaxLines = DETAIL_LINES,
                            toggled = options.subtitles,
                            onClick = { actions.onChoose(options.copy(subtitles = !options.subtitles)) },
                        ),
                        ListItem(
                            icon = Icons.Outlined.Description,
                            label = stringResource(R.string.trickplay_title),
                            detail = stringResource(R.string.backups_trickplay_detail),
                            supportingMaxLines = DETAIL_LINES,
                            toggled = options.trickplay,
                            onClick = { actions.onChoose(options.copy(trickplay = !options.trickplay)) },
                        ),
                    ),
            )
        }
    }
}

/** One backup: when it was made, by which release, the file and what it holds; Restore is last, red and behind a confirm. */
@Composable
internal fun BackupSheet(
    backup: ServerBackup,
    serverVersion: JellyfinVersion?,
    actions: BackupsActions,
    now: Long,
    inWindow: Boolean = true,
) {
    ConsoleSheet(onDismissRequest = { actions.onOpen(null) }, inWindow = inWindow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(
                text = stringResource(R.string.backups_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ItemGroup(
                title = null,
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Outlined.CalendarToday,
                            label = formatRelativeOrAbsolute(backup.createdMillis, now) ?: stringResource(R.string.backups_date_unknown),
                            clickable = false,
                        ),
                        ListItem(
                            icon = Icons.Outlined.Dns,
                            label = stringResource(R.string.backups_made_by, backup.serverVersion),
                            detail = backup.versionNote(serverVersion),
                            supportingMaxLines = DETAIL_LINES,
                            clickable = false,
                        ),
                        ListItem(
                            icon = Icons.Outlined.Inventory2,
                            label = backup.options.summary(),
                            clickable = false,
                        ),
                        ListItem(icon = Icons.Outlined.Description, label = backup.fileName, clickable = false),
                    ),
            )
            BingeTextButton(
                label = stringResource(R.string.backups_restore),
                onClick = { actions.onAskRestore(backup) },
                leadingIcon = Icons.Outlined.RestoreFromTrash,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Restoring, as a confirm that opens on Cancel: the server restarts, takes its database from the backup and loses what
 * came after it, and this app may be signed out. A backup made by another release says so.
 */
@Composable
internal fun RestoreDialog(
    backup: ServerBackup,
    serverVersion: JellyfinVersion?,
    actions: BackupsActions,
    inWindow: Boolean = true,
) {
    ConsoleConfirm(
        title = stringResource(R.string.backups_restore_title),
        message =
            listOfNotNull(
                stringResource(R.string.backups_restore_body),
                backup.versionNote(serverVersion),
            ).joinToString("\n\n"),
        confirmLabel = stringResource(R.string.backups_restore),
        onConfirm = actions.onRestore,
        onDismissRequest = { actions.onAskRestore(null) },
        destructive = true,
        inWindow = inWindow,
    )
}

/** Says so when the backup was made by another release than the running server's; null when the same or unknown. */
@Composable
private fun ServerBackup.versionNote(running: JellyfinVersion?): String? {
    val made = JellyfinVersion.parse(serverVersion) ?: return null
    return if (running != null && made != running) {
        stringResource(R.string.backups_version_differs, "${running.major}.${running.minor}.${running.patch}")
    } else {
        null
    }
}

private const val DETAIL_LINES = 3
