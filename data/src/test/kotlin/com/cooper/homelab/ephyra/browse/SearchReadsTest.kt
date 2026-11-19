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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchReadsTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val latest = JellyfinServerProfile(JellyfinVersion(12, 4, 0))
    private val floor = JellyfinServerProfile(JellyfinVersion(10, 8, 13))
    private val search = JellyfinSearchRepository(client, "user-1", latest)

    @After
    fun close() = jellyfin.close()

    private fun add(
        id: String,
        name: String,
        type: String = "Movie",
    ) = jellyfin.browse.titles.add(FakeTitle(id, name, "lib-movies", type))

    @Test
    fun `titles match the term in every library, by kind, and ask for the card fields`() =
        runBlocking {
            add("a", "Alien")
            add("b", "Aliens")
            add("c", "Blade Runner")
            add("d", "Alien Show", "Series")
            add("e", "Alien Pilot", "Episode")
            val page = search.titles("alien", withTotal = true)
            assertEquals(listOf("a", "b", "d", "e"), page.items.map { it.id })
            assertEquals(4, page.total)
            assertEquals(listOf("d"), search.titles("alien", kinds = setOf(ItemKind.Series)).items.map { it.id })
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("Series", url.queryParameter("includeItemTypes"))
            assertEquals("true", url.queryParameter("recursive"))
            assertEquals("PrimaryImageAspectRatio", url.queryParameter("fields"))
            assertEquals("true", url.queryParameter("enableUserData"))
        }

    @Test
    fun `titles page, and a page past the end is empty`() =
        runBlocking {
            add("a", "Alien")
            add("b", "Aliens")
            val first = search.titles("alien", limit = 1, withTotal = true)
            assertEquals(1, first.nextStart(1))
            assertEquals(listOf("b"), search.titles("alien", startIndex = 1, limit = 1).items.map { it.id })
            assertTrue(search.titles("alien", startIndex = 5).items.isEmpty())
        }

    @Test
    fun `an empty or blank term, and no kinds, answer empty with no request`() =
        runBlocking {
            add("a", "Alien")
            val before = jellyfin.requests.size
            assertTrue(search.titles("").items.isEmpty())
            assertTrue(search.titles("   ").items.isEmpty())
            assertEquals(0, search.titles("   ", withTotal = true).total)
            assertTrue(search.titles("alien", kinds = emptySet()).items.isEmpty())
            assertTrue(search.titles("alien", kinds = setOf(ItemKind.Other)).items.isEmpty())
            assertTrue(search.people("").items.isEmpty())
            assertEquals(before, jellyfin.requests.size)
        }

    @Test
    fun `a one character term is sent, and the term is trimmed`() =
        runBlocking {
            add("a", "Alien")
            assertEquals(listOf("a"), search.titles("  a ").items.map { it.id })
            assertEquals(
                "a",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("searchTerm"),
            )
        }

    @Test
    fun `a term with characters that need encoding arrives as typed`() =
        runBlocking {
            listOf("Tom & Jerry", "50% off", "a+b", "AC/DC", "Amélie", "日本", "what? #1", "q=1;x").forEachIndexed { index, term ->
                add("t$index", term)
                assertEquals(listOf("t$index"), search.titles(term).items.map { it.id })
                assertEquals(
                    term,
                    jellyfin.browse.itemRequests
                        .last()
                        .url
                        .queryParameter("searchTerm"),
                )
            }
        }

    @Test
    fun `people are matched by name and carry their item id`() =
        runBlocking {
            jellyfin.browse.titles.add(FakeTitle("p1", "Sigourney Weaver", "people", "Person"))
            jellyfin.browse.titles.add(FakeTitle("p2", "Weaver Smith", "people", "Person"))
            jellyfin.browse.titles.add(FakeTitle("m1", "Weaver the Movie", "lib-movies"))
            val page = search.people("weaver")
            assertEquals(listOf("p1", "p2"), page.items.map { it.id })
            assertEquals("p-p1", page.items.first().imageTag)
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("/Persons", url.encodedPath)
            assertEquals("user-1", url.queryParameter("userId"))
            assertEquals("0", url.queryParameter("startIndex"))
        }

    @Test
    fun `people page from 12 and are one capped page before it`() =
        runBlocking {
            jellyfin.browse.titles.add(FakeTitle("p1", "Ann One", "people", "Person"))
            jellyfin.browse.titles.add(FakeTitle("p2", "Ann Two", "people", "Person"))
            jellyfin.browse.pagesPeople = true
            assertEquals(listOf("p2"), search.people("ann", startIndex = 1, limit = 1).items.map { it.id })
            val old = JellyfinSearchRepository(client, "user-1", floor)
            val before = jellyfin.requests.size
            assertTrue(old.people("ann", startIndex = 1).items.isEmpty())
            assertEquals("no request for a page the server cannot give", before, jellyfin.requests.size)
            assertEquals(2, old.people("ann").items.size)
            assertNull(
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("startIndex"),
            )
        }

    @Test
    fun `a failed search throws the transport's error, and an unreadable one is unreadable`() {
        add("a", "Alien")
        jellyfin.status["/Items"] = 500
        assertEquals(500, assertThrows(JellyfinHttpException::class.java) { runBlocking { search.titles("alien") } }.code)
        jellyfin.status.clear()
        jellyfin.notJson += "/Persons"
        assertThrows(JellyfinUnreadableAnswer::class.java) { runBlocking { search.people("alien") } }
    }

    @Test
    fun `a search that is cancelled mid-load ends at once, without waiting for the server`() =
        runBlocking {
            add("a", "Alien")
            val held = jellyfin.hold("/Items")
            val running = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) { search.titles("alien") }
            held.awaitArrival()
            running.cancelAndJoin()
            assertTrue(running.isCancelled)
            held.release()
            assertEquals(listOf("a"), search.titles("alien").items.map { it.id })
        }
}
