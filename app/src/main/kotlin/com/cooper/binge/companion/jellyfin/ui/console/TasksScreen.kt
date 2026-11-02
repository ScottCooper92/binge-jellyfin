package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeLoadingIndicator
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.binge.designsystem.R as DesR

/** What the tasks page can ask of its view model, and of the console. */
data class TasksActions(
    val onRun: (ServerTask) -> Unit = {},
    val onStop: (ServerTask) -> Unit = {},
    /** Opens a task's triggers; [onCloseTask] goes back to the list. */
    val onOpen: (ServerTask) -> Unit = {},
    val onCloseTask: () -> Unit = {},
    val onAskAdd: (Boolean) -> Unit = {},
    val onAddTrigger: (TaskTrigger) -> Unit = {},
    val onRemoveTrigger: (Int) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/** Lines a failed run's message takes, so it never moves the list. */
private const val STATUS_LINES = 2

/**
 * The server's scheduled tasks as job rows, grouped by the server's own categories in its order: each
 * run or stopped in place, with how far it has got while it runs, and when it last ran (or failed)
 * while it doesn't. A tap opens the task's own page, with its triggers.
 */
@Composable
fun TasksScreen(
    state: TasksUiState,
    actions: TasksActions,
    now: Long = System.currentTimeMillis(),
) {
    val ready = state as? TasksUiState.Ready
    val open = ready?.open?.let { id -> ready.board.tasks.firstOrNull { it.id == id } }
    BackHandler(enabled = open != null) { actions.onCloseTask() }
    BingeScreenScaffold(
        title = open?.name ?: stringResource(R.string.tasks_title),
        bar = ScreenBar.Small,
        onBack = if (open != null) actions.onCloseTask else actions.onBack,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                state is TasksUiState.Loading ->
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { BingeLoadingIndicator() }
                state is TasksUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                ready != null && open != null -> TaskTriggers(open, ready, actions, inner)
                ready != null -> Tasks(ready, actions, inner, now)
            }
        }
    }
}

@Composable
private fun Tasks(
    state: TasksUiState.Ready,
    actions: TasksActions,
    inner: PaddingValues,
    now: Long,
) {
    val board = state.board
    LazyColumn(contentPadding = inner, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        board.tasks.groupBy { it.category }.forEach { (category, tasks) ->
            item(key = category) {
                ItemGroup(
                    title = category.ifBlank { null },
                    rows = tasks.map { task -> task.row(board, actions, now).item() },
                )
            }
        }
        item {
            Text(
                text = state.actionFailed?.let { stringResource(R.string.tasks_not_sent, stringResource(it.message)) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                minLines = STATUS_LINES,
                maxLines = STATUS_LINES,
            )
        }
    }
}

@Composable
private fun ServerTask.row(
    board: TaskBoard,
    actions: TasksActions,
    now: Long,
): JobRow {
    val last = lastRun
    val idle =
        if (last?.status == TaskStatus.Failed || last?.status == TaskStatus.Aborted) {
            formatRelativeOrAbsolute(last.endedMillis, now)?.let { stringResource(R.string.job_last_failed, it) }
                ?: stringResource(R.string.job_failed)
        } else {
            lastRun(last?.endedMillis, now)
        }
    return JobRow(
        icon = taskIcon(key),
        label = name,
        idleDetail = idle,
        running = board.running(this),
        percent = percent,
        busy = id in board.busy,
        outcome = board.outcomes[id],
        onRun = { actions.onRun(this) },
        onStop = { actions.onStop(this) },
        onOpen = { actions.onOpen(this) },
    )
}

/** A task's icon, from its key; a clock for a task this app doesn't know. */
internal fun taskIcon(key: String): ImageVector =
    when {
        key == "RefreshLibrary" -> Icons.Outlined.VideoLibrary
        key.contains("Audio") -> Icons.Outlined.GraphicEq
        key.contains("Subtitle") -> Icons.Outlined.Subtitles
        key.contains("Lyric") -> Icons.Outlined.Lyrics
        key.contains("People") -> Icons.Outlined.People
        key.contains("Chapter") || key.contains("Trickplay") || key.contains("Image") -> Icons.Outlined.Image
        key.contains("Clean") || key.contains("Delete") -> Icons.Outlined.CleaningServices
        key.contains("Plugin") -> Icons.Outlined.Extension
        key.contains("Guide") || key.contains("Channel") -> Icons.Outlined.LiveTv
        key.contains("Database") || key.contains("Optimi") -> Icons.Outlined.Storage
        else -> Icons.Outlined.Schedule
    }
