package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.LocalIsSinglePaneNav
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinWideSpanishPreview
import com.cooper.binge.companion.jellyfin.server.LogEntry
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.LogLevel
import com.cooper.binge.companion.jellyfin.server.parseLogEntries
import com.cooper.binge.companion.jellyfin.server.redactLog
import java.time.ZoneOffset

private const val LONG_NAME = "FFmpeg.Transcode-2026-10-31_09-15-02_3f2a9c.log"
private const val DAY = 24 * 60 * 60_000L
private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC

private val previewFiles =
    listOf(
        LogFile("log_20261031.log", 412_000, NOW - 60_000),
        LogFile("log_20261030.log", 2_300_000, NOW - DAY),
        LogFile("log_20261029.log", 1_100_000, NOW - 2 * DAY),
    )

private val previewText =
    "[2026-10-31 11:58:02.114 +00:00] [INF] [1] Main: Jellyfin version: 10.11.2\n" +
        "[2026-10-31 11:58:04.552 +00:00] [DBG] [12] Emby.Server.Library.LibraryManager: Validating media library\n" +
        "[2026-10-31 11:59:10.003 +00:00] [WRN] [18] Jellyfin.Api.Auth: Slow query took 4.2 s\n" +
        "[2026-10-31 11:59:41.871 +00:00] [ERR] [21] Jellyfin.Server: Request /Videos/1/stream?api_key=0123456789abcdef failed\n" +
        "System.Net.Http.HttpRequestException: The connection was reset\n" +
        "   at Jellyfin.Server.Stream.Run()\n" +
        "[2026-10-31 11:59:58.020 +00:00] [INF] [4] Jellyfin.Api.SessionController: Session ended\n"

/** Times without millis, so a row prints its raw time and the frame doesn't change as "Yesterday" becomes a date. */
private fun fixedTimes(entries: List<LogEntry>) =
    entries.map { it.copy(timestampMillis = null, timestampRaw = it.timestampRaw?.substring(TIME_AT, TIME_END)) }

private const val TIME_AT = 11
private const val TIME_END = 19

private val previewEntries = fixedTimes(parseLogEntries(redactLog(previewText)))

private fun open(
    query: String = "",
    level: LogLevel = LogLevel.Debug,
    truncated: Boolean = false,
    entries: List<LogEntry> = previewEntries,
    name: String = "log_20261031.log",
    content: LogContent = LogContent.Ready(entries, truncated),
) = LogsUiState.Ready(
    previewFiles,
    OpenLog(name, content, query, level),
)

/** Logs: the file list across the matrix, then a log open, cut short, searched with no match, and the read states. */
class LogsScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun fullName() = Surface { LogNameDialog(LONG_NAME, {}, inWindow = false) }

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun list() = LogsScreen(LogsUiState.Ready(previewFiles), LogsActions(), zone = ZoneOffset.UTC)

    /** A pull in progress: the page stays, with the pull spinner over it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun refreshing() = LogsScreen(LogsUiState.Ready(previewFiles, refreshing = true), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun openLog() = LogsScreen(open(), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun openLogLongName() = LogsScreen(open(name = LONG_NAME), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun openLogLoading() = LogsScreen(open(content = LogContent.Loading), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun openLogFailed() = LogsScreen(open(content = LogContent.Failed(ConsoleFailure.Unreachable)), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun truncated() = LogsScreen(open(truncated = true), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun warningsUp() = LogsScreen(open(level = LogLevel.Warn), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unlabelledEntry() =
        LogsScreen(
            open(entries = fixedTimes(parseLogEntries(redactLog("   at the tail of a cut trace\n" + previewText.lines().first())))),
            LogsActions(),
            zone = ZoneOffset.UTC,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun noMatch() = LogsScreen(open(query = "nothing like this"), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun noFiles() = LogsScreen(LogsUiState.Ready(emptyList()), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun retention() = LogsScreen(LogsUiState.Ready(previewFiles, retention = 3), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun retentionOneDay() = LogsScreen(LogsUiState.Ready(previewFiles, retention = 1), LogsActions(), zone = ZoneOffset.UTC)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun retentionUnsent() =
        LogsScreen(
            LogsUiState.Ready(previewFiles, retention = 7, save = SaveProgress(failed = true)),
            LogsActions(),
            zone = ZoneOffset.UTC,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun retentionSheet() = Surface { RetentionSheet(3, LogsActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LogsScreen(LogsUiState.Loading, LogsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LogsScreen(LogsUiState.Failed(ConsoleFailure.Refused), LogsActions())
}

/**
 * The files without a modified time, so a frame shows no date: a date's format in Spanish differs between JDKs, and a
 * frame recorded on one would fail on another.
 */
private val splitFiles = previewFiles.map { it.copy(modifiedMillis = null) }

@Composable
private fun SplitLogs(state: LogsUiState) =
    CompositionLocalProvider(LocalPageSpansTwoPanes provides true, LocalIsSinglePaneNav provides false) {
        LogsScreen(state, LogsActions(), zone = ZoneOffset.UTC)
    }

/** The logs page on a window with room for two panes: the files beside the open log, or beside its invitation. */
class LogsSplitScreenshotTest {
    /** A log open: its name and entries with their search and chips, and its file marked in the list. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun logOpen() = SplitLogs(open().copy(files = splitFiles))

    /** Nothing open: the files beside the invitation to pick a log. */
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun nothingOpen() = SplitLogs(LogsUiState.Ready(splitFiles))

    /** The open log could not be read: the files stay, and the failure and its Retry fill the detail. */
    @PreviewTest
    @JellyfinWidePreview
    @Composable
    fun readFailed() = SplitLogs(open(content = LogContent.Failed(ConsoleFailure.Unreachable)).copy(files = splitFiles))
}
