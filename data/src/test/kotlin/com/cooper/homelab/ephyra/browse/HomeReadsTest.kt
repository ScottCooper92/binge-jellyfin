package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.JellyfinVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReadsTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val current = JellyfinServerProfile(JellyfinVersion(10, 11, 6))
    private val old = JellyfinServerProfile(JellyfinVersion(10, 8, 13))
    private val resume = JellyfinResumeRepository(client, "user-1", current)
    private val nextUp = JellyfinNextUpRepository(client, "user-1")
    private val latest = JellyfinLatestRepository(client, "user-1", current)
    private val movies = LibraryView("lib-movies", "Movies", LibraryKind.Movies)
    private val shows = LibraryView("lib-shows", "Shows", LibraryKind.TvShows)

    @After
    fun close() = jellyfin.close()

    private fun movie(
        id: String,
        name: String,
        position: Long = 0L,
        played: Boolean = false,
        lastPlayed: Int = 0,
        added: Int = 0,
    ) = jellyfin.browse.titles.add(
        FakeTitle(id, name, "lib-movies", played = played, added = added, positionTicks = position, lastPlayed = lastPlayed),
    )

    private fun episode(
        id: String,
        season: Int,
        number: Int,
        played: Boolean = false,
        series: String = "s1",
    ) = jellyfin.browse.titles.add(
        FakeTitle(
            id,
            "Episode $number",
            "lib-shows",
            "Episode",
            played = played,
            seriesId = series,
            seriesName = "Show $series",
            season = season,
            episode = number,
        ),
    )

    @Test
    fun `resume lists what was started and not finished, most recently played first`() =
        runBlocking {
            movie("a", "Alien", position = 5, lastPlayed = 1)
            movie("b", "Brazil", position = 9, lastPlayed = 3)
            movie("c", "Casablanca", played = true, position = 4, lastPlayed = 9)
            movie("d", "Dune")
            val page = resume.resume()
            assertEquals(listOf("Brazil", "Alien"), page.items.map { it.name })
            assertEquals(
                9L,
                page.items
                    .first()
                    .userData.positionTicks,
            )
            assertNull(page.total)
        }

    @Test
    fun `resume names what it wants, asks for no count unless told, and can be narrowed to a library`() =
        runBlocking {
            movie("a", "Alien", position = 5)
            resume.resume(library = movies, limit = 5, withTotal = true)
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("/UserItems/Resume", url.encodedPath)
            assertEquals("lib-movies", url.queryParameter("parentId"))
            assertEquals("Video", url.queryParameter("mediaTypes"))
            assertEquals("5", url.queryParameter("limit"))
            assertEquals("true", url.queryParameter("enableTotalRecordCount"))
            assertEquals("true", url.queryParameter("enableUserData"))
            assertEquals("1", url.queryParameter("imageTypeLimit"))
            assertEquals("PrimaryImageAspectRatio", url.queryParameter("fields"))
        }

    @Test
    fun `an older server is asked for resume and latest the old way, with the user in the path`() =
        runBlocking {
            movie("a", "Alien", position = 5, added = 1)
            assertEquals(listOf("Alien"), JellyfinResumeRepository(client, "user-1", old).resume().items.map { it.name })
            assertEquals(listOf("Alien"), JellyfinLatestRepository(client, "user-1", old).latest(movies).map { it.name })
            val paths = jellyfin.requests.map { it.url.encodedPath }
            assertTrue("/Users/user-1/Items/Resume" in paths)
            assertTrue("/Users/user-1/Items/Latest" in paths)
            assertFalse("/UserItems/Resume" in paths)
        }

    @Test
    fun `next up is the first unwatched episode of each series being watched`() =
        runBlocking {
            episode("e1", 1, 1, played = true)
            episode("e2", 1, 2)
            episode("e3", 1, 3)
            episode("f1", 1, 1, series = "s2")
            val page = nextUp.nextUp()
            assertEquals(listOf("e2"), page.items.map { it.id })
            val item = page.items.single()
            assertEquals(ItemKind.Episode, item.kind)
            assertEquals("s1", item.seriesId)
            assertEquals("Show s1", item.seriesName)
            assertEquals("sp-s1", item.seriesPrimaryImageTag)
            assertEquals(1, item.seasonNumber)
            assertEquals(2, item.episodeNumber)
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("false", url.queryParameter("enableResumable"))
            assertEquals("false", url.queryParameter("enableRewatching"))
        }

    @Test
    fun `next up is empty when nothing has been watched`() =
        runBlocking {
            episode("e1", 1, 1)
            assertTrue(nextUp.nextUp(library = shows).items.isEmpty())
        }

    @Test
    fun `latest is the newest titles first, a bare array, and can leave out what is watched`() =
        runBlocking {
            movie("a", "Alien", added = 1)
            movie("b", "Brazil", added = 3)
            movie("c", "Casablanca", added = 2, played = true)
            assertEquals(listOf("Brazil", "Casablanca", "Alien"), latest.latest(movies).map { it.name })
            assertEquals(listOf("Brazil", "Alien"), latest.latest(movies, hidePlayed = true).map { it.name })
            assertEquals(listOf("Brazil"), latest.latest(movies, limit = 1).map { it.name })
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("lib-movies", url.queryParameter("parentId"))
            assertEquals("Movie", url.queryParameter("includeItemTypes"))
            assertEquals("true", url.queryParameter("groupItems"))
        }

    @Test
    fun `an empty home is empty lists`() =
        runBlocking {
            movie("a", "Alien")
            assertTrue(resume.resume().items.isEmpty())
            assertTrue(nextUp.nextUp().items.isEmpty())
            assertTrue(latest.latest(shows).isEmpty())
        }

    @Test
    fun `a failed read throws the transport's error, and an answer of the wrong shape is unreadable`() {
        movie("a", "Alien", position = 1)
        jellyfin.status["/UserItems/Resume"] = 500
        assertEquals(500, assertThrows(JellyfinHttpException::class.java) { runBlocking { resume.resume() } }.code)
        jellyfin.status.clear()
        jellyfin.notJson += "/Items/Latest"
        assertThrows(JellyfinUnreadableAnswer::class.java) { runBlocking { latest.latest(movies) } }
    }
}
