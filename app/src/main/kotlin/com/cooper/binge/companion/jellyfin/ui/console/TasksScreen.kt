package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.binge.designsystem.R as DesR

/** What the tasks screens can ask of the view model, and of the console. */
data class TasksActions(
    val onRun: (ServerTask) -> Unit = {},
    val onStop: (ServerTask) -> Unit = {},
    /** Opens a task's own screen, with its triggers. */
    val onOpen: (ServerTask) -> Unit = {},
    val onAskAdd: (Boolean) -> Unit = {},
    val onAddTrigger: (TaskTrigger) -> Unit = {},
    val onRemoveTrigger: (Int) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onBack: () -> Unit = {},
)

/**
 * The server's scheduled tasks as job rows, grouped by the server's own categories in its order: each
 * run or stopped in place, with how far it has got while it runs, and when it last ran (or failed)
 * while it doesn't. A tap opens the task's own screen ([TaskScreen]), above this one on the stack. The task showing,
 * where this list is beside it, is marked.
 */
@Composable
fun TasksScreen(
    state: TasksUiState,
    actions: TasksActions,
    now: Long = System.currentTimeMillis(),
    openTask: String? = LocalOpenItem.current,
) {
    val ready = state as? TasksUiState.Ready
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = stringResource(R.string.tasks_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                state is TasksUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                state is TasksUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                ready != null ->
                    BingePullToRefresh(
                        isRefreshing = ready.refreshing,
                        onRefresh = actions.onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) { Tasks(ready, actions, inner, now, openTask) }
            }
            // A change or run the server didn't take is said in a message, not in a space kept in the list for it.
            RefusedMessage(ready?.refreshFailed, host, R.string.console_not_refreshed)
            RefusedMessage(ready?.actionFailed, host, R.string.tasks_not_sent)
        }
    }
}

/**
 * One task: its description and its triggers, with Add trigger in the footer. A screen of its own above the list on the
 * stack, so Back returns to the list. A task the server no longer lists has nothing to show, and closes.
 */
@Composable
fun TaskScreen(
    state: TasksUiState,
    taskId: String,
    actions: TasksActions,
) {
    val ready = state as? TasksUiState.Ready
    val task = ready?.board?.tasks?.firstOrNull { it.id == taskId }
    val gone = ready != null && task == null
    LaunchedEffect(gone) { if (gone) actions.onBack() }
    val host = remember { SnackbarHostState() }
    BingeScreenScaffold(
        title = task?.name ?: stringResource(R.string.tasks_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        bottomBar = {
            // A task's triggers page creates triggers.
            CreateFooter(
                label = stringResource(R.string.tasks_trigger_add),
                icon = Icons.Outlined.Add,
                onClick = { actions.onAskAdd(true) },
                visible = ready != null && task != null,
                loading = ready?.saving == true,
            )
        },
    ) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when {
                state is TasksUiState.Loading -> ConsoleLoading(inner, LOADING_ROWS)
                state is TasksUiState.Failed -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(inner))
                ready != null && task != null -> TaskTriggers(task, ready, actions, inner)
            }
            RefusedMessage(ready?.triggerFailed, host, R.string.tasks_trigger_not_saved)
        }
    }
}

/** What fills a landscape tablet's third pane while no task is open. */
@Composable
internal fun TaskPlaceholder() {
    EmptyScreen(
        message = stringResource(R.string.tasks_select),
        title = stringResource(R.string.tasks_select_title),
        icon = Icons.Outlined.Schedule,
    )
}

@Composable
private fun Tasks(
    state: TasksUiState.Ready,
    actions: TasksActions,
    inner: PaddingValues,
    now: Long,
    openTask: String?,
) {
    val board = state.board
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = inner,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
    ) {
        if (board.tasks.isEmpty()) {
            item { ConsoleEmptyScreen(stringResource(R.string.tasks_empty), Icons.Outlined.Schedule) }
        } else {
            item { PageIntro(stringResource(R.string.tasks_intro)) }
        }
        board.tasks.groupBy { it.category }.forEach { (category, tasks) ->
            item(key = category) {
                ItemGroup(
                    title = category.ifBlank { null },
                    rows =
                        tasks.map { task ->
                            task.row(task.id == openTask, board, actions, now).item()
                        },
                )
            }
        }
    }
}

@Composable
private fun ServerTask.row(
    selected: Boolean,
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
        selected = selected,
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

private const val LOADING_ROWS = 8
