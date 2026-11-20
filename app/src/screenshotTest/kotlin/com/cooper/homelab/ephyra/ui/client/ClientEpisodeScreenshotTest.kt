package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.CrewJob
import com.cooper.homelab.ephyra.client.title.EpisodeActions
import com.cooper.homelab.ephyra.client.title.EpisodePageUi
import com.cooper.homelab.ephyra.client.title.EpisodeScreen
import com.cooper.homelab.ephyra.client.title.EpisodeStats
import com.cooper.homelab.ephyra.client.title.EpisodeUi
import com.cooper.homelab.ephyra.client.title.EpisodeUiState
import com.cooper.homelab.ephyra.client.title.EpisodesState
import com.cooper.homelab.ephyra.client.title.PersonUi
import com.cooper.homelab.ephyra.client.title.PlayAction
import com.cooper.homelab.ephyra.client.title.PlayKind
import com.cooper.homelab.ephyra.client.title.Resolution
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.client.title.VersionUi
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * The episode page (`:client`) in the states the issue names: loaded with its season's other episodes, resuming a watched favourite, the
 * season's episodes loading and failed, no season to list, loading, and failed. No picture is loaded, so each frame shows the
 * placeholders and is the same every run.
 */
class ClientEpisodeScreenshotTest {
    private fun row(
        number: Int,
        title: String,
        watched: Boolean = false,
        progress: Float? = null,
    ) = EpisodeUi("e$number", 1, number, title, null, TitleImage("e$number", ImageKind.Primary, "p-e$number"), 47, watched, progress)

    private val episode =
        EpisodePageUi(
            id = "e3",
            title = "What the Keeper Knew",
            seriesId = "series",
            seriesName = "The Lighthouse Keepers",
            seasonId = "s1",
            seasonNumber = 1,
            episodeNumber = 3,
            still = TitleImage("e3", ImageKind.Primary, "p-e3"),
            stats = EpisodeStats(rating = 8.6, runtimeMinutes = 47, certification = "TV-14"),
            play = PlayAction(PlayKind.Play, null),
            watched = false,
            favourite = false,
            overview =
                "A letter from the sea sends Wren back to the lighthouse, where the keeper's logbook holds a page nobody admits " +
                    "to having written.",
            airDate = "2020-03-16T00:00:00.0000000Z",
            versions = listOf(version("v1"), version("v2")),
            guestCast =
                listOf(
                    PersonUi("a1", "Jonas Weil", "The Harbourmaster", null),
                    PersonUi("a2", "Ines Marlow", "Keeper's sister", null),
                ),
            crew = listOf(PersonUi("c1", "Tomas Reyes", null, null, CrewJob.Director)),
            season =
                EpisodesState.Ready(
                    listOf(
                        row(1, "The Lamp", watched = true),
                        row(2, "Low Tide", watched = true),
                        row(3, "What the Keeper Knew", progress = 0.4f),
                        row(4, "A Letter From the Sea"),
                        row(5, "Dead Reckoning"),
                    ),
                ),
        )

    private fun version(id: String) =
        VersionUi(id, null, "mkv", Resolution.FullHd, null, 1_800_000_000L, emptyList(), emptyList(), null, null)

    @Composable
    private fun Episode(state: EpisodeUiState) =
        EpisodeScreen(state = state, imageUrl = { _, _ -> null }, actions = EpisodeActions({}, {}, {}, {}, {}, {}, {}, {}, {}))

    private fun ready(page: EpisodePageUi) = EpisodeUiState.Ready(page)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Episode(ready(episode))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun loadedTall() = Episode(ready(episode))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun resumeWatchedFavourite() = Episode(ready(episode.copy(play = PlayAction(PlayKind.Resume, 28), watched = true, favourite = true)))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun seasonLoading() = Episode(ready(episode.copy(season = EpisodesState.Loading)))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun seasonFailed() = Episode(ready(episode.copy(season = EpisodesState.Failed(ServerFailure.Unreachable()))))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun sparseNoSeason() =
        Episode(
            ready(
                episode.copy(
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    still = null,
                    stats = EpisodeStats(null, null, null),
                    overview = null,
                    airDate = null,
                    versions = listOf(version("v1")),
                    guestCast = emptyList(),
                    crew = emptyList(),
                    season = null,
                ),
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Episode(EpisodeUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Episode(EpisodeUiState.Failed(ServerFailure.Unreachable()))
}
