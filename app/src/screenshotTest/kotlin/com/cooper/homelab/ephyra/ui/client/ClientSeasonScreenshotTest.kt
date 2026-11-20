package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.EpisodeUi
import com.cooper.homelab.ephyra.client.title.EpisodesState
import com.cooper.homelab.ephyra.client.title.PlayKind
import com.cooper.homelab.ephyra.client.title.SeasonActions
import com.cooper.homelab.ephyra.client.title.SeasonPageUi
import com.cooper.homelab.ephyra.client.title.SeasonScreen
import com.cooper.homelab.ephyra.client.title.SeasonUiState
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * The season page (`:client`) in the states the issue names: loaded with every episode of the season listed, resuming, all watched, a
 * special with an episode that has no number, the episodes loading and failed, no episodes, loading, and failed. The tall frames hold the
 * whole page. No picture is loaded, so each frame shows the placeholders and is the same every run.
 */
class ClientSeasonScreenshotTest {
    private fun row(
        number: Int?,
        title: String,
        watched: Boolean = false,
        progress: Float? = null,
    ) = EpisodeUi(
        "e${number ?: 0}",
        1,
        number,
        title,
        null,
        TitleImage("e${number ?: 0}", ImageKind.Primary, "p-e${number ?: 0}"),
        47,
        watched,
        progress,
    )

    private val episodes =
        listOf(
            row(1, "The Lamp", watched = true),
            row(2, "Low Tide", watched = true),
            row(3, "What the Keeper Knew", progress = 0.4f),
            row(4, "A Letter From the Sea"),
            row(5, "Dead Reckoning"),
            row(6, "The Long Way Round"),
            row(7, "Salt"),
            row(8, "The Light Goes Out"),
        )

    private val season =
        SeasonPageUi(
            id = "s1",
            name = "Season 1",
            seasonNumber = 1,
            seriesId = "series",
            seriesName = "The Lighthouse Keepers",
            poster = TitleImage("s1", ImageKind.Primary, "p-s1"),
            overview = "The friends inherit the lighthouse, and the first winter shows them what it asks of them.",
            play = SeriesPlay.Episode(PlayKind.Play, "e3", 1, 3, minutesLeft = null),
            watched = false,
            episodes = EpisodesState.Ready(episodes),
        )

    @Composable
    private fun Season(state: SeasonUiState) =
        SeasonScreen(state = state, imageUrl = { _, _ -> null }, actions = SeasonActions({}, {}, {}, {}, {}, {}, {}, {}))

    private fun ready(page: SeasonPageUi) = SeasonUiState.Ready(page)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Season(ready(season))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun loadedTall() = Season(ready(season))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun resumeWatched() =
        Season(ready(season.copy(play = SeriesPlay.Episode(PlayKind.Resume, "e3", 1, 3, minutesLeft = 28), watched = true)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun allWatched() =
        Season(
            ready(
                season.copy(
                    play = SeriesPlay.AllWatched(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, minutesLeft = null)),
                    watched = true,
                    episodes =
                        EpisodesState.Ready(
                            episodes.map {
                                it.copy(watched = true)
                            },
                        ),
                ),
            ),
        )

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun specialsSparse() =
        Season(
            ready(
                season.copy(
                    name = "Specials",
                    seasonNumber = 0,
                    seriesName = null,
                    seriesId = null,
                    poster = null,
                    overview = null,
                    play = SeriesPlay.Episode(PlayKind.Play, "e0", null, null, null),
                    episodes = EpisodesState.Ready(listOf(row(null, "Behind the scenes"), row(1, "Interview"))),
                ),
            ),
        )

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun episodesLoading() = Season(ready(season.copy(play = null, episodes = EpisodesState.Loading)))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun episodesFailed() = Season(ready(season.copy(play = null, episodes = EpisodesState.Failed(ServerFailure.Unreachable()))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noEpisodes() = Season(ready(season.copy(play = SeriesPlay.NoEpisodes, episodes = EpisodesState.Ready(emptyList()))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Season(SeasonUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Season(SeasonUiState.Failed(ServerFailure.Unreachable()))
}
