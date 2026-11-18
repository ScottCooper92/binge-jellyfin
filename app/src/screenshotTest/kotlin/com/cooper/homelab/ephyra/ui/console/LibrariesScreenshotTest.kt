package com.cooper.homelab.ephyra.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.preview.JellyfinWidePreview
import com.cooper.homelab.ephyra.preview.JellyfinWideSpanishPreview
import com.cooper.homelab.ephyra.server.LIBRARY_SCAN_TASK
import com.cooper.homelab.ephyra.server.ServerFolder
import com.cooper.homelab.ephyra.server.ServerLibrary
import com.cooper.homelab.ephyra.server.ServerTask
import com.cooper.homelab.ephyra.server.TaskRun
import com.cooper.homelab.ephyra.server.TaskStatus

private val previewLibraries =
    listOf(
        ServerLibrary("m", "Movies", "movies", listOf("/media/movies")),
        ServerLibrary("s", "Shows", "tvshows", listOf("/media/tv", "/media/anime")),
        ServerLibrary("p", "Family videos", "homevideos", listOf("/media/home")),
    )

private const val NOW = 1_793_534_400_000L // 31 Oct 2026, 12:00 UTC
private const val HOURS_AGO_3 = NOW - 3 * 60 * 60 * 1000

private val previewScanTask =
    ServerTask(
        id = "t",
        key = LIBRARY_SCAN_TASK,
        name = "Scan Media Library",
        description = "",
        category = "Library",
        running = false,
        stopping = false,
        percent = null,
        lastRun = TaskRun(TaskStatus.Completed, HOURS_AGO_3, null),
    )

private val previewReady = LibrariesUiState.Ready(previewLibraries, TaskBoard(listOf(previewScanTask)))

/** Libraries: idle across the matrix, then one scanning beside one done, everything scanning, a failed start, and the read states. */
class LibrariesScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun libraries() = LibrariesScreen(previewReady, LibrariesActions(), now = NOW)

    /** A pull in progress: the list stays, with the pull spinner over it. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun refreshing() = LibrariesScreen(previewReady.copy(refreshing = true), LibrariesActions(), now = NOW)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun scanningOne() =
        LibrariesScreen(
            previewReady.copy(scanning = mapOf("s" to LibraryScan(42, started = true)), outcomes = mapOf("m" to JobOutcome.Succeeded)),
            LibrariesActions(),
            now = NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun scanningAll() =
        LibrariesScreen(
            previewReady.copy(board = TaskBoard(listOf(previewScanTask.copy(running = true, percent = 68)))),
            LibrariesActions(),
            now = NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun scanFailed() =
        LibrariesScreen(
            previewReady.copy(outcomes = mapOf("p" to JobOutcome.Failed)),
            LibrariesActions(),
            now = NOW,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LibrariesScreen(LibrariesUiState.Loading, LibrariesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LibrariesScreen(LibrariesUiState.Failed(ConsoleFailure.Refused), LibrariesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = LibrariesScreen(LibrariesUiState.Ready(emptyList(), TaskBoard(emptyList())), LibrariesActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newLibraryName() =
        Surface {
            AddLibraryForm(listOf("Movies"), saving = false, failed = null, FolderBrowseState.Loading, FolderActions(), { _, _, _ -> }, {})
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newLibraryNameTaken() =
        Surface {
            AddLibraryForm(listOf("Movies"), saving = false, failed = null, FolderBrowseState.Loading, FolderActions(), { _, _, _ -> }, {
            }, startName = "movies")
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun newLibraryKind() =
        Surface {
            AddLibraryForm(
                emptyList(),
                saving = false,
                failed = null,
                FolderBrowseState.Loading,
                FolderActions(),
                { _, _, _ -> },
                {},
                startStep = 1,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newLibraryFolder() =
        Surface {
            AddLibraryForm(
                emptyList(),
                saving = false,
                failed = null,
                FolderBrowseState.Ready(listOf(ServerFolder("media", "/media")), listOf(ServerFolder("movies", "/media/movies"))),
                FolderActions(),
                { _, _, _ -> },
                {},
                startStep = 2,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newLibraryPath() =
        Surface {
            AddLibraryForm(emptyList(), saving = false, failed = ConsoleFailure.Refused, FolderBrowseState.Loading, FolderActions(), {
                _,
                _,
                _,
                ->
            }, {
            }, startStep = 3)
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newLibraryPathMissing() =
        Surface {
            AddLibraryForm(
                emptyList(),
                saving = false,
                failed = null,
                FolderBrowseState.Loading,
                FolderActions(),
                { _, _, _ -> },
                {},
                startStep = 3,
                folderMissing = true,
            )
        }
}

/** The page on a landscape tablet, where a library opens in a third pane: the invitation to pick one while none is. */
class LibrariesPaneScreenshotTest {
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun placeholder() = Surface { LibrariesPlaceholder() }
}
