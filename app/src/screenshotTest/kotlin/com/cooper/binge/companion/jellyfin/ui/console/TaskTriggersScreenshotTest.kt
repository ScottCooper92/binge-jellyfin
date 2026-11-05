package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_HOUR
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_MINUTE
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.server.TriggerKind

private val previewTriggers =
    listOf(
        TaskTrigger.daily(3 * TICKS_PER_HOUR, maxRuntimeTicks = 2 * TICKS_PER_HOUR),
        TaskTrigger.weekly("Sunday", 4 * TICKS_PER_HOUR + 30 * TICKS_PER_MINUTE),
        TaskTrigger.interval(6 * TICKS_PER_HOUR),
        TaskTrigger.interval(45 * TICKS_PER_MINUTE),
        TaskTrigger.startup(),
        TaskTrigger("SystemEventTrigger"),
    )

private fun scan(triggers: List<TaskTrigger>) =
    ServerTask(
        "scan",
        LIBRARY_SCAN_TASK,
        "Scan Media Library",
        "Scans your media library for new files and refreshes metadata.",
        "Library",
        false,
        false,
        null,
        null,
        triggers,
    )

private fun open(
    triggers: List<TaskTrigger> = previewTriggers,
    adding: Boolean = false,
    saving: Boolean = false,
    failed: ConsoleFailure? = null,
) = TasksUiState.Ready(TaskBoard(listOf(scan(triggers))), triggerFailed = failed, adding = adding, saving = saving)

/** One task's triggers across the matrix, with none, saving and failed, then the new trigger form for each kind. */
class TaskTriggersScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun triggers() = TaskScreen(open(), "scan", TasksActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun none() = TaskScreen(open(emptyList()), "scan", TasksActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saving() = TaskScreen(open(saving = true), "scan", TasksActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun newTrigger() = Surface { AddTriggerForm(TasksActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun newWeeklyTrigger() = Surface { AddTriggerForm(TasksActions(), TriggerKind.Weekly) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun newIntervalTrigger() = Surface { AddTriggerForm(TasksActions(), TriggerKind.Interval) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun newStartupTrigger() = Surface { AddTriggerForm(TasksActions(), TriggerKind.Startup) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun newTriggerTwentyFourHour() = Surface { AddTriggerForm(TasksActions(), is24Hour = true) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun newTriggerInvalidTime() =
        Surface {
            TwelveHourTimeInput("13", "00", afternoon = false, onHour = {}, onMinute = {}, onAfternoon = {})
        }
}
