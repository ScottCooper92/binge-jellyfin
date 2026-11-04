package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinWideSpanishPreview
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_HOUR
import com.cooper.binge.companion.jellyfin.server.TaskRun
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.server.TaskTrigger

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val HOUR = 60 * 60 * 1000L

private fun task(
    id: String,
    key: String,
    name: String,
    category: String,
    triggers: List<TaskTrigger> = emptyList(),
) = ServerTask(id, key, name, "", category, false, false, null, TaskRun(TaskStatus.Completed, NOW - 3 * HOUR, null), triggers)

private val splitTasks =
    listOf(
        task(
            "scan",
            LIBRARY_SCAN_TASK,
            "Scan Media Library",
            "Library",
            listOf(TaskTrigger.daily(3 * TICKS_PER_HOUR), TaskTrigger.startup()),
        ),
        task("subs", "DownloadSubtitles", "Download missing subtitles", "Library"),
        task("cache", "DeleteCacheFiles", "Clean Cache Directory", "Maintenance"),
        task("db", "OptimizeDatabaseTask", "Optimise database", "Maintenance"),
    )

private fun split(open: String?) = TasksUiState.Ready(TaskBoard(splitTasks), open = open)

@Composable
private fun SplitTasks(open: String?) =
    CompositionLocalProvider(LocalPageSpansTwoPanes provides true) { TasksScreen(split(open), TasksActions(), now = NOW) }

/** The tasks page on a window with room for two panes: the list beside the open task, or beside its empty state. */
class TasksSplitScreenshotTest {
    /** A task open: its heading and triggers with their own Add trigger, and its row marked in the list. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun taskOpen() = SplitTasks("scan")

    /** Nothing open: the list beside the invitation to pick a task. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun nothingOpen() = SplitTasks(null)
}
