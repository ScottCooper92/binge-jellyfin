package com.cooper.homelab.ephyra.ui.client

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.preview.SOURCE_LOCALE
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.TitleLink
import com.cooper.homelab.ephyra.browse.TitleLinkKind
import com.cooper.homelab.ephyra.client.title.CrewJob
import com.cooper.homelab.ephyra.client.title.EpisodeUi
import com.cooper.homelab.ephyra.client.title.EpisodesState
import com.cooper.homelab.ephyra.client.title.PersonUi
import com.cooper.homelab.ephyra.client.title.PlayKind
import com.cooper.homelab.ephyra.client.title.SeasonUi
import com.cooper.homelab.ephyra.client.title.SeriesActions
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.ephyra.client.title.SeriesScreen
import com.cooper.homelab.ephyra.client.title.SeriesStats
import com.cooper.homelab.ephyra.client.title.SeriesUi
import com.cooper.homelab.ephyra.client.title.SeriesUiState
import com.cooper.homelab.ephyra.client.title.TitleCard
import com.cooper.homelab.ephyra.client.title.TitleFacts
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinScreenshotThemeWrapper
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/** A phone window tall enough to hold the whole page, for the frames that show the episodes below the fold. */
@PreviewWrapper(JellyfinScreenshotThemeWrapper::class)
@Preview(name = "tall", device = "spec:width=411dp,height=2400dp,dpi=420", uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class JellyfinTallPreview

/** A window tall enough to hold the page down to the end of its Details, in English and in Spanish. */
@PreviewWrapper(JellyfinScreenshotThemeWrapper::class)
@Preview(name = "details", device = "spec:width=411dp,height=3600dp,dpi=420", uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "detailses", device = "spec:width=411dp,height=3600dp,dpi=420", uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class JellyfinDetailsPreviews

/**
 * The series page (`:client`) in the states the issue names: loaded, resuming an episode, every episode watched, a single season,
 * episodes loading and failed, loading, and failed. The frames sit here because the Jellyfin brand and the preview matrices are this
 * module's; the screen is `:client`'s. No picture is loaded, so each frame shows the placeholders and is the same every run.
 */
class ClientSeriesScreenshotTest {
    private fun card(
        id: String,
        title: String,
        year: Int,
        played: Boolean = false,
    ) = TitleCard(id, ItemKind.Series, title, year, 8.1, TitleImage(id, ImageKind.Primary, "p-$id"), played)

    private fun season(
        number: Int,
        count: Int,
        unwatched: Int,
    ) = SeasonUi("s$number", number, if (number == 0) "Specials" else "Season $number", count, unwatched, unwatched == 0, null)

    private fun episode(
        number: Int,
        title: String,
        watched: Boolean = false,
        progress: Float? = null,
        runtime: Int? = 47,
        numbered: Boolean = true,
    ) = EpisodeUi(
        id = "e$number",
        season = 1,
        number = number.takeIf { numbered },
        title = title,
        overview = null,
        still = TitleImage("e$number", ImageKind.Primary, "p-e$number"),
        runtimeMinutes = runtime,
        watched = watched,
        progress = progress,
    )

    private val episodes =
        listOf(
            episode(1, "The Lamp", watched = true),
            episode(2, "Low Tide", watched = true),
            episode(3, "What the Keeper Knew", progress = 0.4f),
            episode(4, "A Letter From the Sea"),
            episode(5, "Dead Reckoning", runtime = null),
            episode(6, "The Long Way Round"),
        )

    private val series =
        SeriesUi(
            id = "series",
            title = "The Lighthouse Keepers",
            tagline = "Some lights are better left dark.",
            genres = listOf("Drama", "Mystery"),
            backdrop = TitleImage("series", ImageKind.Backdrop, "b-series"),
            poster = TitleImage("series", ImageKind.Primary, "p-series"),
            stats = SeriesStats(rating = 8.4, year = 2020, seasonCount = 3, certification = "TV-14", episodeCount = 24),
            play = SeriesPlay.Episode(PlayKind.Play, "e3", season = 1, episode = 3, minutesLeft = null),
            watched = false,
            favourite = false,
            overview =
                "Three friends inherit a lighthouse on a coastline that keeps changing. As the seasons turn, they find out what " +
                    "the light was for, and what it costs to put it out.",
            facts =
                TitleFacts(
                    "Los Guardianes del Faro",
                    listOf("Harbour Films", "Northlight"),
                    "2020-03-02T00:00:00.0000000Z",
                    creators = listOf("Ines Calder", "Tomas Rey"),
                    status = "Ended",
                    endDate = "2022-11-18T00:00:00.0000000Z",
                ),
            criticsPercent = 48,
            trailerUrl = "https://example.com/trailer",
            shareUrl = "https://www.themoviedb.org/tv/1",
            tags = listOf("Slow burn", "Atmospheric", "Quiet"),
            links =
                listOf(
                    TitleLink(TitleLinkKind.Imdb, "IMDb", "https://www.imdb.com/title/tt0000001/"),
                    TitleLink(TitleLinkKind.Tmdb, "TheMovieDb", "https://www.themoviedb.org/tv/1"),
                    TitleLink(TitleLinkKind.Other, "Studio", "https://example.com/studio"),
                ),
            cast =
                listOf(
                    PersonUi("a1", "Amara Okafor", "Wren", TitleImage("a1", ImageKind.Primary, "pa1")),
                    PersonUi("a2", "Jonas Weil", "The Harbourmaster", null),
                ),
            crew = listOf(PersonUi("c1", "Tomas Reyes", null, null, CrewJob.Director)),
            seasons = listOf(season(0, 2, 2), season(1, 6, 4), season(2, 8, 8), season(3, 8, 8)),
            selectedSeasonId = "s1",
            episodes = EpisodesState.Ready(episodes),
            similar = listOf(card("x1", "Night Market", 2019), card("x2", "Paper Boats", 2023, true)),
        )

    @Composable
    private fun Series(state: SeriesUiState) =
        SeriesScreen(
            state = state,
            imageUrl = { _, _ -> null },
            actions = SeriesActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Series(SeriesUiState.Ready(series))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun resumeWatchedFavourite() =
        Series(
            SeriesUiState.Ready(
                series.copy(play = SeriesPlay.Episode(PlayKind.Resume, "e3", 1, 3, minutesLeft = 28), watched = true, favourite = true),
            ),
        )

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun loadedTall() = Series(SeriesUiState.Ready(series))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun allWatched() =
        Series(
            SeriesUiState.Ready(
                series.copy(play = SeriesPlay.AllWatched(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null)), watched = true),
            ),
        )

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun singleSeasonSparse() =
        Series(
            SeriesUiState.Ready(
                series.copy(
                    tagline = null,
                    genres = emptyList(),
                    stats = SeriesStats(null, 2020, 1, null),
                    criticsPercent = null,
                    trailerUrl = null,
                    shareUrl = null,
                    tags = emptyList(),
                    links = emptyList(),
                    overview = null,
                    facts = TitleFacts(null, emptyList(), null),
                    cast = emptyList(),
                    crew = emptyList(),
                    seasons = listOf(season(1, 3, 3)),
                    episodes =
                        EpisodesState.Ready(
                            episodes.take(3).mapIndexed { i, e ->
                                if (i ==
                                    1
                                ) {
                                    e.copy(number = null, title = "A special")
                                } else {
                                    e
                                }
                            },
                        ),
                    similar = emptyList(),
                ),
            ),
        )

    @PreviewTest
    @JellyfinDetailsPreviews
    @Composable
    fun endedDetails() = Series(SeriesUiState.Ready(series))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun episodesLoading() = Series(SeriesUiState.Ready(series.copy(episodes = EpisodesState.Loading)))

    @PreviewTest
    @JellyfinTallPreview
    @Composable
    fun episodesFailed() = Series(SeriesUiState.Ready(series.copy(episodes = EpisodesState.Failed(ServerFailure.Unreachable()))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Series(SeriesUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Series(SeriesUiState.Failed(ServerFailure.Unreachable()))
}
