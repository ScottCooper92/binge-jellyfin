package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.TitleLink
import com.cooper.homelab.ephyra.browse.TitleLinkKind
import com.cooper.homelab.ephyra.browse.TrackKind
import com.cooper.homelab.ephyra.client.title.CollectionRow
import com.cooper.homelab.ephyra.client.title.CrewJob
import com.cooper.homelab.ephyra.client.title.MovieActions
import com.cooper.homelab.ephyra.client.title.MovieMediaInfo
import com.cooper.homelab.ephyra.client.title.MovieScreen
import com.cooper.homelab.ephyra.client.title.MovieStats
import com.cooper.homelab.ephyra.client.title.MovieUi
import com.cooper.homelab.ephyra.client.title.MovieUiState
import com.cooper.homelab.ephyra.client.title.PersonUi
import com.cooper.homelab.ephyra.client.title.PlayAction
import com.cooper.homelab.ephyra.client.title.PlayKind
import com.cooper.homelab.ephyra.client.title.Resolution
import com.cooper.homelab.ephyra.client.title.TitleCard
import com.cooper.homelab.ephyra.client.title.TitleFacts
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.client.title.TrackUi
import com.cooper.homelab.ephyra.client.title.VersionUi
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * The movie page (`:client`) in the states the issue names: loaded, resuming a film with several files, watched and a favourite, loading,
 * and failed. The frames sit here because the Jellyfin brand and the preview matrices are this module's; the screen is `:client`'s. No
 * picture is loaded, so each frame shows the placeholders and is the same every run.
 */
class ClientMovieScreenshotTest {
    private fun card(
        id: String,
        title: String,
        year: Int,
        played: Boolean = false,
    ) = TitleCard(id, ItemKind.Movie, title, year, 7.4, TitleImage(id, ImageKind.Primary, "p-$id"), played)

    private val movie =
        MovieUi(
            id = "m1",
            title = "The Long Quiet",
            tagline = "Some silences are loud.",
            genres = listOf("Drama", "Mystery", "Thriller"),
            backdrop = TitleImage("m1", ImageKind.Backdrop, "b-m1"),
            poster = TitleImage("m1", ImageKind.Primary, "p-m1"),
            stats = MovieStats(rating = 7.8, year = 2021, runtimeMinutes = 118, certification = "PG-13"),
            play = PlayAction(PlayKind.Play, null),
            watched = false,
            favourite = false,
            overview =
                "A lighthouse keeper on a remote island receives a letter that should not exist, and the sender is someone " +
                    "the island buried a long time ago. As the weather closes in, the keeper has to decide what to believe.",
            facts =
                TitleFacts(
                    "Le Grand Silence",
                    listOf("Harbour Films", "Northlight"),
                    "2021-04-09T00:00:00.0000000Z",
                    directors = listOf("Mira Lund"),
                ),
            criticsPercent = 87,
            trailerUrl = "https://example.com/trailer",
            shareUrl = "https://www.themoviedb.org/movie/1",
            tags = listOf("Slow burn", "Atmospheric", "Quiet"),
            links =
                listOf(
                    TitleLink(TitleLinkKind.Imdb, "IMDb", "https://www.imdb.com/title/tt0000001/"),
                    TitleLink(TitleLinkKind.Tmdb, "TheMovieDb", "https://www.themoviedb.org/movie/1"),
                    TitleLink(TitleLinkKind.Other, "Studio", "https://example.com/studio"),
                ),
            versions = listOf(version("v1"), version("v2")),
            cast =
                listOf(
                    PersonUi("a1", "Amara Okafor", "Wren", TitleImage("a1", ImageKind.Primary, "pa1")),
                    PersonUi("a2", "Jonas Weil", "The Harbourmaster", null),
                    PersonUi("a3", "Ines Marlow", "Keeper's sister", null),
                ),
            crew =
                listOf(
                    PersonUi("c1", "Tomas Reyes", null, null, CrewJob.Director),
                    PersonUi("c2", "Mei Tanaka", null, null, CrewJob.Writer),
                ),
            collection =
                CollectionRow(
                    "box",
                    "The Quiet Collection",
                    listOf(card("m3", "The Longer Quiet", 2023), card("m4", "Quiet Waters", 2019, true)),
                ),
            similar = listOf(card("m2", "Night Market", 2019), card("m5", "Paper Boats", 2023, true), card("m6", "Salt Road", 2017)),
        )

    private fun version(id: String) =
        VersionUi(id, null, "mkv", Resolution.FullHd, null, 4_000_000_000L, emptyList(), emptyList(), null, null)

    @Composable
    private fun Movie(state: MovieUiState) =
        MovieScreen(
            state = state,
            imageUrl = { _, _ -> null },
            actions = MovieActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Movie(MovieUiState.Ready(movie))

    @PreviewTest
    @JellyfinDetailsPreviews
    @Composable
    fun details() = Movie(MovieUiState.Ready(movie))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun resumeWatchedFavourite() =
        Movie(MovieUiState.Ready(movie.copy(play = PlayAction(PlayKind.Resume, 42), watched = true, favourite = true)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sparse() =
        Movie(
            MovieUiState.Ready(
                movie.copy(
                    tagline = null,
                    genres = emptyList(),
                    stats = MovieStats(null, 2021, null, null),
                    criticsPercent = null,
                    trailerUrl = null,
                    shareUrl = null,
                    tags = emptyList(),
                    links = emptyList(),
                    overview = null,
                    facts = TitleFacts(null, emptyList(), null),
                    versions = listOf(version("v1")),
                    cast = emptyList(),
                    crew = emptyList(),
                    collection = null,
                    similar = emptyList(),
                ),
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Movie(MovieUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Movie(MovieUiState.Failed(ServerFailure.Unreachable()))

    private fun track(
        index: Int,
        kind: TrackKind,
        label: String,
    ) = TrackUi(index, kind, label, isDefault = index == 1, isForced = false, isExternal = false, isHearingImpaired = false)

    private val detailed =
        VersionUi(
            id = "v2",
            name = null,
            container = "mkv",
            resolution = Resolution.Uhd,
            range = "HDR10",
            sizeBytes = 14_800_000_000L,
            audio = listOf(track(1, TrackKind.Audio, "English · TRUEHD 7.1"), track(2, TrackKind.Audio, "Spanish · AC3 5.1")),
            subtitles = listOf(track(3, TrackKind.Subtitle, "English"), track(4, TrackKind.Subtitle, "Spanish")),
            defaultAudioIndex = 1,
            defaultSubtitleIndex = null,
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun mediaInfo() = MovieMediaInfo(detailed)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun mediaInfoSparse() = MovieMediaInfo(version("v1").copy(resolution = null, sizeBytes = null))
}
