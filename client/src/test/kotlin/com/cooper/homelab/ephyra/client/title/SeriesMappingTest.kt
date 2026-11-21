package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.NextEpisode
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail
import com.cooper.homelab.ephyra.browse.TitleLink
import com.cooper.homelab.ephyra.browse.TitleLinkKind
import com.cooper.homelab.ephyra.browse.TitlePerson
import com.cooper.homelab.ephyra.server.TICKS_PER_MINUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesMappingTest {
    private fun season(
        id: String,
        number: Int,
        count: Int? = 3,
        unplayed: Int? = null,
    ) = BrowseItem(
        id,
        "Season $number",
        ItemKind.Season,
        seasonNumber = number,
        childCount = count,
        userData = ItemUserData(unplayedCount = unplayed),
        imageTags = mapOf(ImageKind.Primary to "t$id"),
    )

    private fun episode(
        id: String,
        number: Int,
        seasonId: String = "s1",
        played: Boolean = false,
        position: Long = 0L,
    ) = BrowseItem(
        id,
        "Episode $number",
        ItemKind.Episode,
        seasonNumber = 1,
        episodeNumber = number,
        seasonId = seasonId,
        runTimeTicks = 50 * TICKS_PER_MINUTE,
        overview = " ",
        parentBackdropItemId = "series",
        parentBackdropImageTag = "bd",
        userData = ItemUserData(played = played, positionTicks = position),
    )

    private fun seriesDetail(
        status: String?,
        endDate: String? = null,
        people: List<TitlePerson> = emptyList(),
    ) = TitleDetail(
        item = BrowseItem("series", "Show", ItemKind.Series, year = 2020),
        originalTitle = null,
        overview = null,
        taglines = emptyList(),
        genres = emptyList(),
        studios = listOf("Network"),
        criticRating = 59.6,
        premiereDate = "2020-03-02T00:00:00Z",
        endDate = endDate,
        status = status,
        externalIds = emptyMap(),
        people = people,
        versions = emptyList(),
        chapters = emptyList(),
        seasonName = null,
    )

    @Test
    fun `a series counts its episodes without the specials, and carries its creators, status and end date in the facts`() {
        val seasons = listOf(season("sp", 0, count = 2), season("s1", 1, count = 10), season("s2", 2, count = 4))
        val people = listOf(TitlePerson("c", "Jo Maker", null, PersonKind.Creator, null))
        val ended = seriesUi(seriesDetail("Ended", "2021-11-01T00:00:00Z", people), seasons, null, emptyList(), null, EpisodesState.Loading)
        assertEquals(14, ended.stats.episodeCount)
        assertEquals(2, ended.stats.seasonCount)
        assertEquals(60, ended.criticsPercent)
        assertEquals(listOf("Jo Maker"), ended.facts.creators)
        assertEquals("Ended", ended.facts.status)
        assertEquals("2021-11-01T00:00:00Z", ended.facts.endDate)
        assertTrue(ended.crew.isEmpty())
        val running =
            seriesUi(seriesDetail("Continuing", "2021-11-01T00:00:00Z"), emptyList(), null, emptyList(), null, EpisodesState.Loading)
        assertNull(running.facts.endDate)
        assertNull(running.stats.episodeCount)
    }

    @Test
    fun `a series carries its trailer, tags, links and the public page to share`() {
        val link = TitleLink(TitleLinkKind.Tmdb, "TheMovieDb", "https://www.themoviedb.org/tv/1")
        val detail =
            seriesDetail("Ended").copy(
                trailerUrl = "https://youtu.be/abc",
                tags = listOf("Slow burn"),
                links = listOf(link),
                externalIds = mapOf("Tmdb" to "95396"),
            )
        val series = seriesUi(detail, emptyList(), null, emptyList(), null, EpisodesState.Loading)
        assertEquals("https://youtu.be/abc", series.trailerUrl)
        assertEquals(listOf("Slow burn"), series.tags)
        assertEquals(listOf(link), series.links)
        assertEquals("https://www.themoviedb.org/tv/95396", series.shareUrl)
        val bare = seriesUi(seriesDetail("Ended"), emptyList(), null, emptyList(), null, EpisodesState.Loading)
        assertNull(bare.trailerUrl)
        assertNull(bare.shareUrl)
        assertTrue(bare.tags.isEmpty() && bare.links.isEmpty())
    }

    @Test
    fun `a season is its own number, with specials marked and its counts carried`() {
        val specials = seasonUi(season("sp", 0, count = 2, unplayed = 2))
        assertTrue(specials.isSpecials)
        assertEquals(2, specials.unwatched)
        val first = seasonUi(season("s1", 1))
        assertFalse(first.isSpecials)
        assertEquals(TitleImage("s1", ImageKind.Primary, "ts1"), first.poster)
        assertEquals(3, first.episodeCount)
    }

    @Test
    fun `an episode shows its own still, else its series' backdrop, and how far through it the user is`() {
        val plain = episodeUi(episode("e1", 1))
        assertEquals(TitleImage("series", ImageKind.Backdrop, "bd"), plain.still)
        assertNull(plain.overview)
        assertEquals(50, plain.runtimeMinutes)
        assertNull(plain.progress)
        assertEquals(0.5f, episodeUi(episode("e2", 2, position = 25 * TICKS_PER_MINUTE)).progress)
        assertNull(episodeUi(episode("e3", 3, played = true, position = 25 * TICKS_PER_MINUTE)).progress)
        val own = episodeUi(episode("e4", 4).copy(imageTags = mapOf(ImageKind.Primary to "p")))
        assertEquals(TitleImage("e4", ImageKind.Primary, "p"), own.still)
    }

    @Test
    fun `the button follows what to play next`() {
        assertNull(seriesPlay(null))
        assertEquals(SeriesPlay.NoEpisodes, seriesPlay(NextEpisode.None))
        assertEquals(
            SeriesPlay.AllWatched(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null, "Episode 1")),
            seriesPlay(NextEpisode.AllWatched(episode("e1", 1))),
        )
        assertEquals(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null, "Episode 1"), seriesPlay(NextEpisode.Start(episode("e1", 1))))
        assertEquals(SeriesPlay.Episode(PlayKind.Play, "e2", 1, 2, null, "Episode 2"), seriesPlay(NextEpisode.Next(episode("e2", 2))))
        assertEquals(
            SeriesPlay.Episode(PlayKind.Resume, "e3", 1, 3, 25, "Episode 3"),
            seriesPlay(NextEpisode.Resume(episode("e3", 3, position = 25 * TICKS_PER_MINUTE))),
        )
    }

    @Test
    fun `the season shown first holds the episode to play, else the first that is not specials, else the first`() {
        val seasons = listOf(season("sp", 0), season("s1", 1), season("s2", 2))
        assertEquals("s2", initialSeason(seasons, NextEpisode.Next(episode("e", 1, seasonId = "s2"))))
        assertEquals("s1", initialSeason(seasons, NextEpisode.AllWatched(episode("e1", 1))))
        assertEquals("s1", initialSeason(seasons, null))
        assertEquals("sp", initialSeason(listOf(season("sp", 0)), null))
        assertNull(initialSeason(emptyList(), null))
    }
}
