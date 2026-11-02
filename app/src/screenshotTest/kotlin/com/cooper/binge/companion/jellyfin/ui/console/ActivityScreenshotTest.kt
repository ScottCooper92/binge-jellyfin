package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.Severity
import java.time.ZoneOffset

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val MINUTE = 60_000L
private const val DAY = 24 * 60 * MINUTE

private val previewEntries =
    listOf(
        ActivityEntry(7, "Ana is playing Severance on Living room", null, "VideoPlayback", NOW - 5 * MINUTE, Severity.Information),
        ActivityEntry(
            6,
            "Failed login attempt from guest",
            "IP address: 192.168.1.40",
            "AuthenticationFailed",
            NOW - 42 * MINUTE,
            Severity.Error,
        ),
        ActivityEntry(
            5,
            "Ana is online from Living room",
            "IP address: 192.168.1.23",
            "SessionStarted",
            NOW - 50 * MINUTE,
            Severity.Information,
        ),
        ActivityEntry(4, "Download missing subtitles failed", null, "ScheduledTaskFailed", NOW - DAY, Severity.Warning),
        ActivityEntry(3, "Password changed for sam", null, "UserPasswordChanged", NOW - DAY - 3 * 60 * MINUTE, Severity.Information),
        ActivityEntry(
            2,
            "sam has disconnected from Pixel",
            "IP address: 192.168.1.31",
            "SessionEnded",
            NOW - 3 * DAY,
            Severity.Information,
        ),
    )

private val previewReady = ActivityUiState.Ready(previewEntries, total = previewEntries.size)

/** Activity: grouped by day across the matrix, then more to come, a failed next page, empty, and the read states. */
class ActivityScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun activity() = ActivityScreen(previewReady, ActivityActions(), now = NOW, zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun moreFailed() =
        ActivityScreen(
            previewReady.copy(total = 40, moreFailed = ConsoleFailure.Unreachable),
            ActivityActions(),
            now = NOW,
            zone = ZoneOffset.UTC,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = ActivityScreen(ActivityUiState.Ready(emptyList(), total = 0), ActivityActions(), now = NOW, zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ActivityScreen(ActivityUiState.Loading, ActivityActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ActivityScreen(ActivityUiState.Failed(ConsoleFailure.Refused), ActivityActions())
}
