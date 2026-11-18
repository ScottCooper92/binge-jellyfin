package com.cooper.homelab.ephyra.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenPreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.server.ActivityEntry
import com.cooper.homelab.ephyra.server.LibraryStorage
import com.cooper.homelab.ephyra.server.ServerInfo
import com.cooper.homelab.ephyra.server.ServerPaths
import com.cooper.homelab.ephyra.server.ServerStorage
import com.cooper.homelab.ephyra.server.Severity
import com.cooper.homelab.ephyra.server.StorageFolder
import com.cooper.homelab.ephyra.server.StorageKind
import java.time.ZoneOffset

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

    /** The recent alerts, a warning beside an error with and without a line of detail, and the calm row when there are none. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun alerts() =
        Surface {
            AlertsGroup(
                listOf(
                    ActivityEntry(
                        3,
                        "Scheduled task failed",
                        "Scan media library",
                        "ScheduledTaskFailed",
                        1_794_000_000_000L,
                        Severity.Error,
                    ),
                    ActivityEntry(2, "Slow query", null, "TaskFailed", 1_793_990_000_000L, Severity.Warning),
                    ActivityEntry(
                        1,
                        "Failed login attempt",
                        "from 192.168.1.40",
                        "AuthenticationFailed",
                        1_793_900_000_000L,
                        Severity.Warning,
                    ),
                ),
                zone = ZoneOffset.UTC,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun noAlerts() = Surface { AlertsGroup(emptyList(), zone = ZoneOffset.UTC) }

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
