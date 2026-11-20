package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.NextEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesMappingTest {
    private val minute = 600_000_000L

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
        runTimeTicks = 50 * minute,
        overview = " ",
        parentBackdropItemId = "series",
        parentBackdropImageTag = "bd",
        userData = ItemUserData(played = played, positionTicks = position),
    )

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
        assertEquals(0.5f, episodeUi(episode("e2", 2, position = 25 * minute)).progress)
        assertNull(episodeUi(episode("e3", 3, played = true, position = 25 * minute)).progress)
        val own = episodeUi(episode("e4", 4).copy(imageTags = mapOf(ImageKind.Primary to "p")))
        assertEquals(TitleImage("e4", ImageKind.Primary, "p"), own.still)
    }

    @Test
    fun `the button follows what to play next`() {
        assertNull(seriesPlay(null))
        assertEquals(SeriesPlay.NoEpisodes, seriesPlay(NextEpisode.None))
        assertEquals(SeriesPlay.AllWatched, seriesPlay(NextEpisode.AllWatched))
        assertEquals(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null), seriesPlay(NextEpisode.Start(episode("e1", 1))))
        assertEquals(SeriesPlay.Episode(PlayKind.Play, "e2", 1, 2, null), seriesPlay(NextEpisode.Next(episode("e2", 2))))
        assertEquals(
            SeriesPlay.Episode(PlayKind.Resume, "e3", 1, 3, 25),
            seriesPlay(NextEpisode.Resume(episode("e3", 3, position = 25 * minute))),
        )
    }

    @Test
    fun `the season shown first holds the episode to play, else the first that is not specials, else the first`() {
        val seasons = listOf(season("sp", 0), season("s1", 1), season("s2", 2))
        assertEquals("s2", initialSeason(seasons, NextEpisode.Next(episode("e", 1, seasonId = "s2"))))
        assertEquals("s1", initialSeason(seasons, NextEpisode.AllWatched))
        assertEquals("s1", initialSeason(seasons, null))
        assertEquals("sp", initialSeason(listOf(season("sp", 0)), null))
        assertNull(initialSeason(emptyList(), null))
    }
}
