package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.ServerInfo

private val previewInfo =
    ServerInfo(
        name = "truenas",
        version = "10.11.6",
        address = "http://192.168.86.38:8096",
        architecture = "X64",
        operatingSystem = "Linux",
        updateAvailable = false,
        restartPending = false,
        canRestart = true,
    )

private val previewReady = ServerUiState.Ready(previewInfo)

/** The server: its info across the matrix, then waiting on an update and a restart, each restart state, and the read states. */
class ServerScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun server() = ServerScreen(previewReady, ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun waiting() = ServerScreen(ServerUiState.Ready(previewInfo.copy(updateAvailable = true, restartPending = true)), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun confirming() = Surface { RestartDialog(previewInfo, ServerActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun restarting() = ServerScreen(previewReady.copy(restart = Restart.Restarting), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun restarted() = ServerScreen(previewReady.copy(restart = Restart.Restarted), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun notBack() = ServerScreen(previewReady.copy(restart = Restart.NotBack), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun confirmingShutdown() = Surface { ShutdownDialog(previewInfo, ServerActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun shutDown() = ServerScreen(previewReady.copy(shutdown = Shutdown.ShutDown), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun shutdownFailed() = ServerScreen(previewReady.copy(shutdown = Shutdown.Failed), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun cantRestart() = ServerScreen(ServerUiState.Ready(previewInfo.copy(canRestart = false, operatingSystem = "")), ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ServerScreen(ServerUiState.Loading, ServerActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ServerScreen(ServerUiState.Failed(ConsoleFailure.Refused), ServerActions())
}
