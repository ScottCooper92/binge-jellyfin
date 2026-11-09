package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.ActivityFilter
import com.cooper.binge.companion.jellyfin.server.ActivityTimePreset
import com.cooper.binge.companion.jellyfin.server.ActivityWho
import com.cooper.binge.companion.jellyfin.server.Severity

private const val NOW = 1_762_700_000_000L
private val entry = ActivityEntry(1, "alice signed in", "192.168.1.5", "SessionStarted", NOW - 3_600_000L, Severity.Information)
private val warning = ActivityEntry(2, "Playback failed", "Living room TV", "VideoPlaybackStopped", NOW - 7_200_000L, Severity.Warning)

private val applied =
    ActivityFilter(
        fromMillis = NOW - 604_800_000L,
        preset = ActivityTimePreset.Last7Days,
        who = ActivityWho.Users,
        username = "alice",
        severity = Severity.Warning,
        type = "VideoPlaybackStopped",
    )

private val draft = ActivityFilter(fromMillis = NOW - 604_800_000L, preset = ActivityTimePreset.Last7Days, severity = Severity.Error)
private val users = ActivityUsers.Ready(listOf("alice", "bob", "Claude", "sam"))

/** The activity filters: the page with its chips and nothing matching, and each page of the sheet. */
class ActivityFiltersScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun chips() =
        ActivityScreen(
            ActivityUiState.Ready(listOf(warning), 1),
            ActivityActions(),
            now = NOW,
            filters = ActivityFilters(applied = applied, draft = applied),
            extendedFilters = true,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun nothingMatches() =
        ActivityScreen(
            ActivityUiState.Ready(emptyList(), 0),
            ActivityActions(),
            now = NOW,
            filters = ActivityFilters(applied = applied, draft = applied),
            extendedFilters = true,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun filteredLoading() =
        ActivityScreen(ActivityUiState.Loading, ActivityActions(), now = NOW, filters = ActivityFilters(applied = applied, draft = applied))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noFilter() = ActivityScreen(ActivityUiState.Ready(listOf(entry, warning), 2), ActivityActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun sheet() = Surface { MainPage(draft, extended = true, push = {}, actions = ActivityFilterActions()) }

    /** An older server: only the time and the system or user split. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sheetOlderServer() = Surface { MainPage(draft, extended = false, push = {}, actions = ActivityFilterActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun time() = Surface { TimePage(draft, extended = true, push = {}, pop = {}, actions = ActivityFilterActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun who() =
        Surface {
            WhoPage(ActivityFilters(draft = applied, users = users), extended = true, pop = {}, actions = ActivityFilterActions())
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun whoLoading() =
        Surface {
            WhoPage(
                ActivityFilters(draft = applied, users = ActivityUsers.Loading),
                extended = true,
                pop = {},
                actions = ActivityFilterActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun severity() = Surface { SeverityPage(draft, pop = {}, actions = ActivityFilterActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun type() = Surface { TypePage(applied, pop = {}, actions = ActivityFilterActions()) }
}
