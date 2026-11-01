package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.LogFile
import java.time.ZoneOffset

private const val DAY = 24 * 60 * 60_000L
private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC

private val previewFiles =
    listOf(
        LogFile("log_20261031.log", 412_000, NOW - 60_000),
        LogFile("log_20261030.log", 2_300_000, NOW - DAY),
        LogFile("log_20261029.log", 1_100_000, NOW - 2 * DAY),
    )

private val previewLines =
    listOf(
        "[2026-10-31 11:58:02.114 +00:00] [INF] [1] Main: Jellyfin version: 10.11.2",
        "[2026-10-31 11:58:04.552 +00:00] [INF] [12] Emby.Server.Implementations.Library.LibraryManager: Validating media library",
        "[2026-10-31 11:59:10.003 +00:00] [WRN] [18] Jellyfin.Api.Auth: Slow query took 4.2 s",
        "[2026-10-31 11:59:41.871 +00:00] [ERR] [21] Jellyfin.Server: Request to /Videos/1/stream?api_key=[redacted] failed",
        "[2026-10-31 11:59:58.020 +00:00] [INF] [4] Jellyfin.Api.Controllers.SessionController: Session ended",
    )

private fun open(
    query: String = "",
    problemsOnly: Boolean = false,
    truncated: Boolean = false,
) = LogsUiState.Ready(
    previewFiles,
    OpenLog("log_20261031.log", LogContent.Ready(previewLines, truncated), query, problemsOnly),
)

/** Logs: the file list across the matrix, then a log open, cut short, searched with no match, and the read states. */
class LogsScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun list() = LogsScreen(LogsUiState.Ready(previewFiles), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun openLog() = LogsScreen(open(), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun truncated() = LogsScreen(open(truncated = true), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun problemsOnly() = LogsScreen(open(problemsOnly = true), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun noMatch() = LogsScreen(open(query = "nothing like this"), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LogsScreen(LogsUiState.Loading, LogsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LogsScreen(LogsUiState.Failed(ConsoleFailure.Refused), LogsActions())
}
