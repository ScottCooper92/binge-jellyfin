package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskRun
import com.cooper.binge.companion.jellyfin.server.TaskStatus

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val HOUR = 60 * 60 * 1000L

private fun task(
    id: String,
    key: String,
    name: String,
    category: String,
    status: TaskStatus? = TaskStatus.Completed,
    hoursAgo: Long = 3,
) = ServerTask(id, key, name, "", category, false, false, null, status?.let { TaskRun(it, NOW - hoursAgo * HOUR, null) })

private val previewTasks =
    listOf(
        task("scan", LIBRARY_SCAN_TASK, "Scan Media Library", "Library", hoursAgo = 2),
        task("subs", "DownloadSubtitles", "Download missing subtitles", "Library", hoursAgo = 20),
        task("chapters", "RefreshChapterImages", "Extract Chapter Images", "Library", status = null),
        task("cache", "DeleteCacheFiles", "Clean Cache Directory", "Maintenance", TaskStatus.Failed, hoursAgo = 30),
        task("db", "OptimizeDatabaseTask", "Optimise database", "Maintenance", hoursAgo = 50),
        task("plugins", "PluginUpdates", "Update Plugins", "Application", hoursAgo = 5),
    )

private val previewReady = TasksUiState.Ready(TaskBoard(previewTasks))

/** Scheduled tasks: idle across the matrix, then one running beside outcomes, a failed run, and the read states. */
class TasksScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun tasks() = TasksScreen(previewReady, TasksActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun running() =
        TasksScreen(
            TasksUiState.Ready(
                TaskBoard(
                    previewTasks.map { if (it.id == "subs") it.copy(running = true, percent = 37) else it },
                    outcomes = mapOf("scan" to JobOutcome.Succeeded, "db" to JobOutcome.Failed),
                ),
            ),
            TasksActions(),
            now = NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notSent() = TasksScreen(previewReady.copy(actionFailed = ConsoleFailure.Unreachable), TasksActions(), now = NOW)

    /** The server showed no tasks: every one it listed was hidden, which its own dashboard leaves out too. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = TasksScreen(TasksUiState.Ready(TaskBoard(emptyList())), TasksActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = TasksScreen(TasksUiState.Loading, TasksActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = TasksScreen(TasksUiState.Failed(ConsoleFailure.Refused), TasksActions())
}
