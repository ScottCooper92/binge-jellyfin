package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinWidePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinWideSpanishPreview
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.TypeFetchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private val configuration =
    Json
        .parseToJsonElement(
            """{"MetadataOptions":[{"ItemType":"Movie","DisabledMetadataFetchers":[],"DisabledImageFetchers":["The Open Movie Database"]},
            {"ItemType":"MusicVideo","DisabledMetadataFetchers":["The Open Movie Database"],"DisabledImageFetchers":["The Open Movie Database"]},
            {"ItemType":"Book"}]}""",
        ).jsonObject

private val fetchers =
    listOf(
        TypeFetchers("Movie", listOf("TheMovieDb", "The Open Movie Database"), listOf("TheMovieDb", "The Open Movie Database")),
        TypeFetchers("MusicVideo", listOf("TheMovieDb", "The Open Movie Database"), listOf("The Open Movie Database")),
        TypeFetchers("Series", listOf("TheTVDB", "TheMovieDb"), listOf("TheTVDB")),
    )

private val ready = MetadataDefaultsUiState.Ready(configuration, fetchers)

/** The metadata providers by type: the list across the matrix, a type's page, a type the server named no providers for, then the other states. */
class MetadataDefaultsScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinSpanishPreviews
    @Composable
    fun list() = MetadataDefaultsScreen(ready, MetadataDefaultsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun typePage() = MetadataTypeScreen(ready, "MusicVideo", MetadataDefaultsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun fetcherPage() =
        MetadataFetcherScreen(ready, "MusicVideo", OpenFetcher(FetcherKind.Metadata, "The Open Movie Database"), MetadataDefaultsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unnamedType() = MetadataTypeScreen(ready, "Book", MetadataDefaultsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun saving() = MetadataTypeScreen(ready.copy(save = SaveProgress(SaveStatus.Saving)), "Movie", MetadataDefaultsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = MetadataDefaultsScreen(MetadataDefaultsUiState.Loading, MetadataDefaultsActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = MetadataDefaultsScreen(MetadataDefaultsUiState.Failed(ConsoleFailure.Unreachable), MetadataDefaultsActions())
}

/** The page on a landscape tablet, where a type opens in a third pane: the invitation to pick one while none is. */
class MetadataDefaultsPaneScreenshotTest {
    @PreviewTest
    @JellyfinWidePreview
    @JellyfinWideSpanishPreview
    @Composable
    fun placeholder() = Surface { MetadataDefaultsPlaceholder() }
}
