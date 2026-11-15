package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.LibraryStorage
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.server.ServerPaths
import com.cooper.binge.companion.jellyfin.server.ServerStorage
import com.cooper.binge.companion.jellyfin.server.StorageFolder
import com.cooper.binge.companion.jellyfin.server.StorageKind

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

    /** The space of the server's folders and a library's, with one disk nearly full, and the server's paths. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun storageAndPaths() =
        ServerScreen(
            ServerUiState.Ready(
                previewInfo.copy(
                    paths = ServerPaths(programData = "/config", cache = "/cache", logs = "/config/log", web = "/jellyfin/web"),
                ),
                ServerStorage(
                    listOf(
                        StorageKind.ProgramData to StorageFolder("/config", 100_000_000_000L, 400_000_000_000L, "ext4"),
                        StorageKind.Cache to StorageFolder("/cache", 95_000_000_000L, 5_000_000_000L, "zfs"),
                    ),
                    listOf(LibraryStorage("Movies", listOf(StorageFolder("/media/movies", 3_000_000_000_000L, 1_000_000_000_000L, null)))),
                ),
            ),
            ServerActions(),
        )

    /** A pull in progress: the page stays, with the pull spinner over it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun refreshing() = ServerScreen(previewReady.copy(refreshing = true), ServerActions())

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
    @JellyfinSpanishPreviews
    @Composable
    fun confirmingShutdown() = Surface { ShutdownDialog(previewInfo, ServerActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun restartNotBack() = Surface { RestartNotBackDialog(ServerActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun shutDown() = ServerScreen(previewReady.copy(shutdown = Shutdown.ShutDown), ServerActions())

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
