package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.server.AvailableProviders
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.MetadataSaver
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
        metadataSavers = listOf(MetadataSaver("Nfo", false)),
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
    "DisabledSubtitleFetchers":["Legendas"],"EnableTrickplayImageExtraction":true,"SaveTrickplayWithMedia":false,
    "EnableEmbeddedExtrasTitles":true,"AllowEmbeddedSubtitles":"AllowText","MetadataSavers":["Nfo"],
    "SubtitleDownloadLanguages":["eng","fre"]}"""

private const val MUSIC_OPTIONS =
    """{"EnableLUFSScan":true,"SaveLyricsWithMedia":true,"DisabledLyricFetchers":[],"PreferNonstandardArtistsTag":true,
    "UseCustomTagDelimiters":true,"CustomTagDelimiters":["/","|",";"],"DelimiterWhitelist":["AC/DC","Back in Black"]}"""

private fun ready(
    type: String = "movies",
    name: String = "Movies",
    options: String = movieOptions,
) = LibraryUiState.Ready(
    library = library(type, name, options),
    options = library(type, name, options).options,
    available = providers,
    cultures =
        listOf(
            Culture("English", "en", listOf("eng")),
            Culture("French", "fr", listOf("fre", "fra")),
            Culture("German", "de", listOf("ger", "deu")),
            Culture("Italian", "it", listOf("ita")),
            Culture("Portuguese", "pt", listOf("por")),
            Culture("Spanish", "es", listOf("spa")),
        ),
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

    /** The metadata group: embedded titles for extras, and a switch for each metadata saver. They sit below the page frame's first screen. */
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun metadata() = Surface { Column { MetadataGroup(ready(), LibraryActions(), newest) {} } }

    /** The subtitles group, with the embedded subtitle types the library offers. It sits below the page frame's first screen. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun subtitles() = Surface { Column { VideoGroups(ready(), LibraryActions(), newest) {} } }

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun languages() = LibraryScreen(ready().copy(page = LibraryPage.Languages), LibraryActions(), newest)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun languagesNone() = LibraryScreen(ready().copy(page = LibraryPage.Languages, cultures = emptyList()), LibraryActions(), newest)

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
            ready("music", "Music", MUSIC_OPTIONS),
            LibraryActions(),
            newest,
        )

    /** The audio tag options of a music library. They sit below the page frame's first screen. */
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun audioTags() = Surface { Column { AudioTagsGroup(ready("music", "Music", MUSIC_OPTIONS), LibraryActions(), newest) } }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun tagDelimiters() = Surface { TagDelimitersForm("/|;", LibraryActions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun delimiterWhitelist() = Surface { DelimiterWhitelistForm("AC/DC\nBack in Black", LibraryActions()) }

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
    fun saving() = LibraryScreen(ready().copy(save = SaveProgress(SaveStatus.Saving)), LibraryActions(), newest)

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
