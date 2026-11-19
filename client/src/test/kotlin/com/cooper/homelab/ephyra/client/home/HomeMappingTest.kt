package com.cooper.homelab.ephyra.client.home

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.LibraryView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMappingTest {
    private val latest = HomeRowKey.Latest("lib-movies", "Movies")

    private fun movie(
        id: String = "m1",
        backdrop: Boolean = true,
        userData: ItemUserData = ItemUserData(),
        runTimeTicks: Long? = 100L,
    ) = BrowseItem(
        id = id,
        name = "Alien",
        kind = ItemKind.Movie,
        year = 1979,
        communityRating = 8.5,
        runTimeTicks = runTimeTicks,
        imageTags =
            buildMap {
                put(ImageKind.Primary, "p-$id")
                if (backdrop) put(ImageKind.Backdrop, "b-$id")
            },
        userData = userData,
    )

    private fun episode(
        backdrop: Boolean = false,
        number: Int? = 3,
    ) = BrowseItem(
        id = "e3",
        name = "Half Loop",
        kind = ItemKind.Episode,
        seriesId = "s1",
        seriesName = "Severance",
        seriesPrimaryImageTag = "sp-s1",
        parentBackdropItemId = "s1".takeIf { backdrop },
        parentBackdropImageTag = "sb-s1".takeIf { backdrop },
        seasonNumber = 1,
        episodeNumber = number,
        imageTags = mapOf(ImageKind.Primary to "still-e3"),
    )

    @Test
    fun `the rows are Continue watching, Next up, then a Latest for each library of movies or shows`() {
        val libraries =
            listOf(
                LibraryView("a", "Movies", LibraryKind.Movies),
                LibraryView("b", "Collections", LibraryKind.BoxSets),
                LibraryView("c", "Shows", LibraryKind.TvShows),
                LibraryView("d", "Home videos", LibraryKind.Other),
            )
        assertEquals(
            listOf(HomeRowKey.ContinueWatching, HomeRowKey.NextUp, HomeRowKey.Latest("a", "Movies"), HomeRowKey.Latest("c", "Shows")),
            homeRowKeys(libraries),
        )
        assertEquals(listOf(HomeRowKey.ContinueWatching, HomeRowKey.NextUp), homeRowKeys(emptyList()))
    }

    @Test
    fun `a row with nothing to show is left out`() {
        assertNull(homeRow(HomeRowKey.NextUp, emptyList()))
        assertEquals(1, homeRow(latest, listOf(movie()))?.cards?.size)
    }

    @Test
    fun `a movie card has its year, poster, backdrop and rating, and opens its page`() {
        val card = movie().toHomeCard(latest)
        assertEquals("Alien", card.title)
        assertEquals(HomeCaption.Year(1979), card.caption)
        assertEquals(HomeImage("m1", ImageKind.Primary, "p-m1"), card.poster)
        assertEquals(HomeImage("m1", ImageKind.Backdrop, "b-m1"), card.backdrop)
        assertEquals(8.5, card.rating!!, 0.0)
        assertEquals(false, card.resumes)
    }

    @Test
    fun `an episode card is titled by its series, shows the series poster, and captions its place and name`() {
        val card = episode().toHomeCard(HomeRowKey.NextUp)
        assertEquals("Severance", card.title)
        assertEquals(HomeCaption.Episode(1, 3, "Half Loop"), card.caption)
        assertEquals(HomeImage("s1", ImageKind.Primary, "sp-s1"), card.poster)
    }

    @Test
    fun `an episode with no backdrop of its own takes its series'`() {
        assertEquals(HomeImage("s1", ImageKind.Backdrop, "sb-s1"), episode(backdrop = true).toHomeCard(HomeRowKey.NextUp).backdrop)
        assertNull(episode(backdrop = false).toHomeCard(HomeRowKey.NextUp).backdrop)
    }

    @Test
    fun `only a Continue watching card resumes`() {
        assertTrue(movie().toHomeCard(HomeRowKey.ContinueWatching).resumes)
        assertEquals(false, movie().toHomeCard(HomeRowKey.NextUp).resumes)
    }

    @Test
    fun `progress is the server's percentage, else the position over the runtime, and none once watched or not begun`() {
        assertEquals(0.25f, movie(userData = ItemUserData(playedPercentage = 25.0)).toHomeCard(latest).progress!!, 0.0001f)
        assertEquals(0.4f, movie(userData = ItemUserData(positionTicks = 40L)).toHomeCard(latest).progress!!, 0.0001f)
        assertNull(movie(userData = ItemUserData(played = true, playedPercentage = 90.0)).toHomeCard(latest).progress)
        assertNull(movie().toHomeCard(latest).progress)
        assertNull(movie(userData = ItemUserData(positionTicks = 40L), runTimeTicks = null).toHomeCard(latest).progress)
        assertEquals(1f, movie(userData = ItemUserData(playedPercentage = 140.0)).toHomeCard(latest).progress!!, 0.0001f)
    }

    @Test
    fun `an unnumbered episode and a yearless movie are captioned as far as they can be`() {
        assertEquals(HomeCaption.Episode(1, null, "Half Loop"), episode(number = null).toHomeCard(HomeRowKey.NextUp).caption)
        assertEquals(HomeCaption.None, movie().copy(year = null).toHomeCard(latest).caption)
    }

    @Test
    fun `the hero is the first Continue watching card with a backdrop, else the first latest one`() {
        val resumed = homeRow(HomeRowKey.ContinueWatching, listOf(movie("a", backdrop = false), movie("b")))!!
        val fresh = homeRow(latest, listOf(movie("c")))!!
        assertEquals("b", pickHero(listOf(fresh, resumed))?.card?.id)
        assertEquals(HomeImage("b", ImageKind.Backdrop, "b-b"), pickHero(listOf(fresh, resumed))?.backdrop)
        assertEquals("c", pickHero(listOf(fresh))?.card?.id)
    }

    @Test
    fun `no backdrop anywhere is no hero, and Next up never fronts Home`() {
        assertNull(pickHero(listOf(homeRow(latest, listOf(movie(backdrop = false)))!!)))
        assertNull(pickHero(listOf(homeRow(HomeRowKey.NextUp, listOf(movie()))!!)))
        assertNull(pickHero(emptyList()))
    }

    @Test
    fun `Home is loading until a row lands, ready with the rows after, and empty once every row settled with nothing`() {
        assertEquals(HomeUiState.Loading, homeState(emptyList(), pending = 3))
        assertEquals(HomeUiState.Empty, homeState(emptyList(), pending = 0))
        val row = homeRow(latest, listOf(movie()))!!
        val ready = homeState(listOf(row), pending = 2) as HomeUiState.Ready
        assertEquals(listOf(row), ready.rows)
        assertEquals(2, ready.pending)
        assertEquals("m1", ready.hero?.card?.id)
    }
}
