package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenAction
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.binge.designsystem.R as DesR

/** What the libraries page can ask of its view model, and of the console. */
data class LibrariesActions(
    val onScan: (ServerLibrary) -> Unit = {},
    val onScanEverything: () -> Unit = {},
    val onStopEverything: () -> Unit = {},
    val onOpen: (ServerLibrary) -> Unit = {},
    val onAskAdd: (Boolean) -> Unit = {},
    val onAdd: (name: String, kind: String, path: String) -> Unit = { _, _, _ -> },
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Rows in the page's skeleton while it loads. */
private const val LOADING_ROWS = 6

/** Lines a failed scan's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

/**
 * The server's libraries as job rows, after binge-seerr's jobs: the scan of everything (the "Scan
 * Media Library" task, which can be stopped), then each library with its own scan. A scan in
 * progress says how far it has got, and its outcome shows for a moment when it ends.
 */
@Composable
fun LibrariesScreen(
    state: LibrariesUiState,
    actions: LibrariesActions,
    now: Long = System.currentTimeMillis(),
    folders: FolderBrowseState = FolderBrowseState.Loading,
    folderActions: FolderActions = FolderActions(),
) {
    BingeScreenScaffold(title = stringResource(R.string.libraries_title), bar = ScreenBar.Small, onBack = actions.onBack) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LibrariesUiState.Loading -> ListRowSkeletonColumn(contentPadding = inner, count = LOADING_ROWS)
                is LibrariesUiState.Failed -> ConsoleFailed(state.failure, actions.onRetry, inner)
                is LibrariesUiState.Ready -> {
                    if (state.libraries.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(R.string.libraries_empty),
                            title = stringResource(R.string.libraries_empty_title),
                            action = ScreenAction(stringResource(R.string.libraries_add), { actions.onAskAdd(true) }, Icons.Outlined.Add),
                        )
                    } else {
                        Libraries(state, actions, inner, now)
                    }
                    if (state.adding) {
                        AddLibrarySheet(
                            names = state.libraries.map { it.name },
                            saving = state.saving,
                            failed = state.addFailed,
                            folders = folders,
                            folderActions = folderActions,
                            onAdd = actions.onAdd,
                            onDismiss = { actions.onAskAdd(false) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Libraries(
    state: LibrariesUiState.Ready,
    actions: LibrariesActions,
    inner: PaddingValues,
    now: Long,
) {
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
                Text(stringResource(R.string.libraries_body), style = MaterialTheme.typography.bodyMedium)
                BingeOutlinedButton(
                    label = stringResource(R.string.libraries_add),
                    onClick = { actions.onAskAdd(true) },
                    leadingIcon = Icons.Outlined.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        state.board.task(LIBRARY_SCAN_TASK)?.let { task ->
            item {
                val row =
                    JobRow(
                        icon = Icons.Outlined.VideoLibrary,
                        label = stringResource(R.string.libraries_scan_all),
                        idleDetail = lastRun(task.lastRun?.endedMillis, now),
                        running = state.board.running(task),
                        percent = task.percent,
                        busy = task.id in state.board.busy,
                        outcome = state.board.outcomes[task.id],
                        runLabel = R.string.libraries_scan,
                        onRun = actions.onScanEverything,
                        onStop = actions.onStopEverything,
                    )
                ItemGroup(title = null, rows = listOf(row.item()))
            }
        }
        item {
            ItemGroup(
                title = stringResource(R.string.libraries_group),
                rows = state.libraries.map { library -> library.row(state, actions).item() },
            )
        }
        item {
            Text(
                text =
                    state.actionFailed
                        ?.let {
                            val title = if (state.stopFailed) R.string.libraries_not_stopped else R.string.libraries_not_started
                            stringResource(title, stringResource(it.message))
                        }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                minLines = STATUS_LINES,
                maxLines = STATUS_LINES,
            )
        }
    }
}

@Composable
private fun ServerLibrary.row(
    state: LibrariesUiState.Ready,
    actions: LibrariesActions,
): JobRow {
    val scan = state.scanning[id]
    return JobRow(
        icon = typeIcon(type),
        label = name.ifBlank { stringResource(R.string.libraries_unnamed) },
        idleDetail = folders.joinToString(", "),
        running = scan != null,
        percent = scan?.percent?.takeIf { it > 0 },
        outcome = state.outcomes[id],
        runLabel = R.string.libraries_scan,
        onRun = { actions.onScan(this) },
        onOpen = { actions.onOpen(this) },
    )
}

/** When a task last ran, or that it hasn't. */
@Composable
internal fun lastRun(
    endedMillis: Long?,
    now: Long,
): String =
    formatRelativeOrAbsolute(endedMillis, now)?.let { stringResource(R.string.job_last_run, it) }
        ?: stringResource(R.string.job_never_run)

/** A library's icon, from its `CollectionType`; a folder for mixed content or a type this app doesn't know. */
internal fun typeIcon(type: String): ImageVector =
    when (type) {
        "movies" -> Icons.Outlined.Movie
        "tvshows" -> Icons.Outlined.Tv
        "music" -> Icons.Outlined.LibraryMusic
        "books" -> Icons.Outlined.Book
        "homevideos", "photos" -> Icons.Outlined.Photo
        else -> Icons.Outlined.Folder
    }
