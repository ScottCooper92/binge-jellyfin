package com.cooper.homelab.ephyra.ui.console

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.diagnostics.LogEntry
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.presentation.diagnostics.DiagnosticsEmpty
import com.cooper.homelab.core.presentation.diagnostics.DiagnosticsState
import com.cooper.homelab.core.uiandroid.diagnostics.DiagnosticsScreen
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import kotlin.time.Instant

private val preamble = listOf("App: 1.0.0 (1)", "Android: 15 (API 35)", "Device: Google Pixel 9")

private fun entry(
    seconds: Long,
    level: LogLevel,
    event: String,
) = LogEntry(Instant.fromEpochSeconds(1_800_000_000 + seconds), level, "Connection", event, emptyList(), null)

/** The Diagnostics page over the app's own log: the lines above the log, then the entries newest first, and the empty state. */
class DiagnosticsScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun diagnosticsLog() =
        page(
            DiagnosticsState.Ready(
                listOf(
                    entry(30, LogLevel.Error, "call failed"),
                    entry(20, LogLevel.Warn, "retrying"),
                    entry(10, LogLevel.Info, "signed in"),
                ),
                LogLevel.Info,
                preamble,
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun diagnosticsEmpty() = page(DiagnosticsState.Empty(DiagnosticsEmpty.NothingLogged, LogLevel.Info, preamble))

    @Composable
    private fun page(state: DiagnosticsState) =
        DiagnosticsScreen(state, {}, {}, {}, onBack = {}, snackbarHostState = remember { SnackbarHostState() })
}
