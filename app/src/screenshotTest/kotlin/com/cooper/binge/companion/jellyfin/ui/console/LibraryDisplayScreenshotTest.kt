package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.DisplayList
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.ListEntry
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import com.cooper.binge.companion.jellyfin.server.UiLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val newest = JellyfinServerProfile.from("10.11.6")

private val configuration =
    Json
        .parseToJsonElement(
            """{"UICulture":"en-US","EnableFolderView":true,"EnableGroupingMoviesIntoCollections":true,"ImageSavingConvention":"Compatible",
            "MetadataPath":"/config/metadata","ChapterImageResolution":"P720","DummyChapterDuration":300,"SortRemoveWords":["the","a","an"],
            "SortReplaceCharacters":[".","+","%"],"PathSubstitutions":[{"From":"/media","To":"/mnt/media"}]}""",
        ).jsonObject

private val ready =
    LibraryDisplayUiState.Ready(
        configuration,
        listOf(UiLanguage("English (United States)", "en-US"), UiLanguage("Español", "es")),
    )

/** Library display and metadata: the page across the matrix, an older server, each list editor and sheet, then the other states. */
class LibraryDisplayScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun display() = LibraryDisplayScreen(ready, LibraryDisplayActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun olderServer() = LibraryDisplayScreen(ready, LibraryDisplayActions(), JellyfinServerProfile.from("10.8.13"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saveFailed() = LibraryDisplayScreen(ready.copy(saveFailed = ConsoleFailure.Unreachable), LibraryDisplayActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun listEditor() =
        LibraryDisplayScreen(
            ready.copy(editing = DisplayList.RemoveWords, draft = listOf(ListEntry("the"), ListEntry("a"), ListEntry("an"))),
            LibraryDisplayActions(),
            newest,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun substitutionsEditor() =
        LibraryDisplayScreen(
            ready.copy(editing = DisplayList.Substitutions, draft = listOf(ListEntry("/media", "/mnt/media"))),
            LibraryDisplayActions(),
            newest,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun emptyEditor() = LibraryDisplayScreen(ready.copy(editing = DisplayList.ReplaceCharacters), LibraryDisplayActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun newWord() = Surface { NewEntryForm(DisplayList.RemoveWords, LibraryDisplayActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newSubstitutionFrom() = Surface { NewEntryForm(DisplayList.Substitutions, LibraryDisplayActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun newSubstitutionTo() = Surface { NewEntryForm(DisplayList.Substitutions, LibraryDisplayActions(), startStep = 1) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun dummyChapters() = Surface { ChapterDurationForm(300, LibraryDisplayActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun metadataFolder() =
        Surface {
            MetadataPathForm(
                "/config/metadata",
                FolderBrowseState.Ready(listOf(ServerFolder("config", "/config")), listOf(ServerFolder("metadata", "/config/metadata"))),
                FolderActions(),
                LibraryDisplayActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun metadataFolderTyped() =
        Surface {
            MetadataPathForm("/config/metadata", FolderBrowseState.Loading, FolderActions(), LibraryDisplayActions(), startTyping = true)
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LibraryDisplayScreen(LibraryDisplayUiState.Loading, LibraryDisplayActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LibraryDisplayScreen(LibraryDisplayUiState.Failed(ConsoleFailure.Refused), LibraryDisplayActions(), newest)
}
