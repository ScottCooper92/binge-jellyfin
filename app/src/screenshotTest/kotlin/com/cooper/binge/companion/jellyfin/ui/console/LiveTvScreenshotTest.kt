package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TunerHostSummary

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val DAY = 24 * 60 * 60 * 1000L

private val previewSummary =
    LiveTvSummary(
        tuners =
            listOf(
                TunerHostSummary("t1", "Living room HDHomeRun", "hdhomerun", "http://192.168.1.50"),
                TunerHostSummary("t2", "IPTV", "m3u", "https://iptv.example:8443"),
                TunerHostSummary("t3", "Cable box", "somePlugin", "http://cable.lan"),
            ),
        providers =
            listOf(
                GuideProviderSummary("g1", "SchedulesDirect", "alice"),
                GuideProviderSummary("g2", "xmltv", "/config/guide.xml"),
            ),
        guideEndMillis = NOW + 7 * DAY,
    )

private val withTasks = LiveTvActions(onOpenTasks = {})

/** Live TV: the page across the matrix, then what is empty, and what a user who cannot open Scheduled tasks sees. */
class LiveTvScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun liveTv() = LiveTvScreen(LiveTvUiState.Ready(previewSummary), withTasks, now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = LiveTvScreen(LiveTvUiState.Ready(LiveTvSummary()), withTasks, now = NOW)

    /** A user who manages Live TV but cannot open Scheduled tasks: no Refresh guide row, and no guide end where they cannot ask. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun withoutTasks() = LiveTvScreen(LiveTvUiState.Ready(previewSummary.copy(guideEndMillis = null)), LiveTvActions(), now = NOW)

    /** An administrator who manages Live TV also sees the Recording row. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun withRecording() =
        LiveTvScreen(LiveTvUiState.Ready(previewSummary), LiveTvActions(onOpenTasks = {}, onOpenRecording = {}), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LiveTvScreen(LiveTvUiState.Loading, LiveTvActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LiveTvScreen(LiveTvUiState.Failed(ConsoleFailure.Unreachable), LiveTvActions(), now = NOW)
}
