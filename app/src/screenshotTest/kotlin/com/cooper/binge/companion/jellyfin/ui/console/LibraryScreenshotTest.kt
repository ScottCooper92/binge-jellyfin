package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.AvailableProviders
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val newest = JellyfinServerProfile.from("10.11.6")

private val providers =
    AvailableProviders(
        metadataReaders = listOf("Nfo"),
        subtitleFetchers = listOf("Open Subtitles", "Podnapisi", "Legendas"),
        lyricFetchers = listOf("LrcLib"),
        segmentProviders = listOf("Chapter Segments"),
    )

private fun library(
    type: String,
    name: String,
    options: String,
) = ServerLibrary("id-$type", name, type, listOf("/media/$type", "/mnt/more/$type"), Json.parseToJsonElement(options).jsonObject)

private val movieOptions =
    """{"Enabled":true,"EnableRealtimeMonitor":true,"SaveLocalMetadata":false,"AutomaticRefreshIntervalDays":30,
    "PreferredMetadataLanguage":"en","MetadataCountryCode":"GB","EnableEmbeddedTitles":true,"AutomaticallyAddToCollection":true,
    "SkipSubtitlesIfEmbeddedSubtitlesPresent":true,"RequirePerfectSubtitleMatch":true,"SubtitleFetcherOrder":["Podnapisi"],
    "DisabledSubtitleFetchers":["Legendas"],"EnableTrickplayImageExtraction":true,"SaveTrickplayWithMedia":false}"""

private fun ready(
    type: String = "movies",
    name: String = "Movies",
    options: String = movieOptions,
) = LibraryUiState.Ready(
    library = library(type, name, options),
    options = library(type, name, options).options,
    available = providers,
    cultures = listOf(Culture("English", "en", listOf("eng"))),
    countries = listOf(Country("GB", "United Kingdom")),
)

/** A library's page: movies across the matrix, then each kind, an older server, the pages of its providers, its sheets and confirms, and the read states. */
class LibraryScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun library() = LibraryScreen(ready(), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun shows() =
        LibraryScreen(
            ready("tvshows", "Shows", """{"EnableAutomaticSeriesGrouping":true,"SeasonZeroDisplayName":"Extras"}"""),
            LibraryActions(),
            newest,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun music() =
        LibraryScreen(
            ready("music", "Music", """{"EnableLUFSScan":true,"SaveLyricsWithMedia":true,"DisabledLyricFetchers":[]}"""),
            LibraryActions(),
            newest,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun homeVideos() = LibraryScreen(ready("homevideos", "Home videos", """{"EnablePhotos":true}"""), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun olderServer() = LibraryScreen(ready(), LibraryActions(), JellyfinServerProfile.from("10.8.13"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saveFailed() =
        LibraryScreen(ready().copy(saveFailed = ConsoleFailure.Unreachable, failed = ConsoleFailure.Refused), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun providersList() = LibraryScreen(ready().copy(page = LibraryPage.Providers(ProviderGroup.Subtitles)), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun provider() = LibraryScreen(ready().copy(page = LibraryPage.One(ProviderGroup.Subtitles, "Podnapisi")), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun providerLast() = LibraryScreen(ready().copy(page = LibraryPage.One(ProviderGroup.Subtitles, "Legendas")), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun rename() = Surface { RenameForm("Movies", saving = false, taken = false, LibraryActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun renameTaken() = Surface { RenameForm("Movies", saving = false, taken = true, LibraryActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun specialsName() = Surface { SeasonZeroForm("Extras", LibraryActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun removeLibrary() = Surface { RemoveLibraryDialog("Movies", LibraryActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun removeFolder() = Surface { RemoveFolderDialog("/mnt/more/movies", LibraryActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun addFolder() =
        Surface {
            AddFolderForm(
                ready(),
                FolderBrowseState.Ready(
                    listOf(ServerFolder("media", "/media")),
                    listOf(ServerFolder("movies", "/media/movies"), ServerFolder("tv", "/media/tv")),
                ),
                FolderActions(),
                LibraryActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun addFolderFromTheDrives() =
        Surface {
            AddFolderForm(
                ready(),
                FolderBrowseState.Ready(emptyList(), listOf(ServerFolder("media", "/media"))),
                FolderActions(),
                LibraryActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun addFolderEmpty() =
        Surface {
            AddFolderForm(
                ready(),
                FolderBrowseState.Ready(listOf(ServerFolder("movies", "/media/movies")), emptyList()),
                FolderActions(),
                LibraryActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun addFolderFailed() =
        Surface {
            AddFolderForm(
                ready().copy(failed = ConsoleFailure.Refused),
                FolderBrowseState.Failed(ConsoleFailure.Unreachable),
                FolderActions(),
                LibraryActions(),
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun addFolderLoading() =
        Surface { AddFolderForm(ready().copy(busy = true), FolderBrowseState.Loading, FolderActions(), LibraryActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun addFolderTyped() =
        Surface {
            AddFolderForm(
                ready().copy(folderMissing = true),
                FolderBrowseState.Loading,
                FolderActions(),
                LibraryActions(),
                startTyping = true,
            )
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = LibraryScreen(LibraryUiState.Loading, LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = LibraryScreen(LibraryUiState.Failed(ConsoleFailure.Refused), LibraryActions(), newest)
}
