package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedReadsTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val latest = JellyfinServerProfile(JellyfinVersion(12, 4, 0))
    private val floor = JellyfinServerProfile(JellyfinVersion(10, 8, 13))
    private val titles = JellyfinTitleRepository(client, "user-1", latest)
    private val people = JellyfinPersonRepository(client, "user-1", latest)
    private val collections = JellyfinCollectionRepository(client, "user-1", latest)

    @After
    fun close() = jellyfin.close()

    private fun movie(
        id: String,
        name: String,
        year: Int,
        person: String? = null,
        collection: String? = null,
    ) = jellyfin.browse.titles.add(
        FakeTitle(
            id,
            name,
            "lib-movies",
            year = year,
            personIds = listOfNotNull(person),
            collectionIds = listOfNotNull(collection),
        ),
    )

    @Test
    fun `similar titles exclude the title itself and respect the limit`() =
        runBlocking {
            movie("a", "Alien", 1979)
            movie("b", "Blade Runner", 1982)
            movie("c", "Contact", 1997)
            assertEquals(listOf("b", "c"), titles.similar("a").map { it.id })
            assertEquals(1, titles.similar("a", limit = 1).size)
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("/Items/a/Similar", url.encodedPath)
            assertEquals("user-1", url.queryParameter("userId"))
            assertEquals("PrimaryImageAspectRatio,ChildCount", url.queryParameter("fields"))
        }

    @Test
    fun `a person is read by id through the item route, on either route generation`() =
        runBlocking {
            jellyfin.browse.titles.add(
                FakeTitle(
                    "p1",
                    "Sigourney Weaver",
                    "people",
                    "Person",
                    extra = """"Overview":"Actor.","PremiereDate":"1949-10-08T00:00:00.0000000Z","ProductionLocations":["New York, USA"]""",
                ),
            )
            listOf(latest, floor).forEach { profile ->
                val person = JellyfinPersonRepository(client, "user-1", profile).person("p1")!!
                assertEquals("Sigourney Weaver", person.name)
                assertEquals("Actor.", person.overview)
                assertEquals("New York, USA", person.birthPlace)
                assertNull(person.deathDate)
            }
            val paths = jellyfin.requests.map { it.url.encodedPath }
            assertTrue("/Items/p1" in paths && "/Users/user-1/Items/p1" in paths)
            assertEquals(404, assertThrows(JellyfinHttpException::class.java) { runBlocking { people.person("nobody") } }.code)
        }

    @Test
    fun `the titles a person is in are paged by person id`() =
        runBlocking {
            movie("a", "Alien", 1979, person = "p1")
            movie("b", "Blade Runner", 1982)
            movie("c", "Contact", 1997, person = "p1")
            val page = people.titlesWith("p1", limit = 1, withTotal = true)
            assertEquals(listOf("a"), page.items.map { it.id })
            assertEquals(2, page.total)
            assertEquals(1, page.nextStart(1))
            assertEquals(listOf("c"), people.titlesWith("p1", startIndex = 1, limit = 1).items.map { it.id })
            assertTrue(people.titlesWith("nobody").items.isEmpty())
            val url =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("Movie,Series", url.queryParameter("includeItemTypes"))
            assertEquals("true", url.queryParameter("recursive"))
        }

    @Test
    fun `a collection lists its parts in release order`() =
        runBlocking {
            jellyfin.browse.titles.add(FakeTitle("box", "Alien Collection", "lib-box", "BoxSet"))
            jellyfin.browse.titles.add(FakeTitle("a2", "Aliens", "box", year = 1986))
            jellyfin.browse.titles.add(FakeTitle("a1", "Alien", "box", year = 1979))
            assertEquals(listOf("a1", "a2"), collections.parts("box").map { it.id })
            assertEquals(
                "box",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("parentId"),
            )
            assertTrue(collections.parts("empty").isEmpty())
        }

    @Test
    fun `the collections a movie is in come from the server's route where it has one`() =
        runBlocking {
            jellyfin.browse.titles.add(FakeTitle("box", "Alien Collection", "lib-box", "BoxSet"))
            movie("a", "Alien", 1979, collection = "box")
            movie("b", "Blade Runner", 1982)
            assertEquals(listOf("box"), collections.collectionsOf("a").map { it.id })
            assertEquals(emptyList<BrowseItem>(), collections.collectionsOf("b"))
        }

    @Test
    fun `before 12_0 a hand-made box set with no provider ids is found by asking each box set for its members`() =
        runBlocking {
            val before = JellyfinCollectionRepository(client, "user-1", floor)
            jellyfin.browse.titles.add(FakeTitle("box", "Sol Levante Collection", "lib-box", "BoxSet"))
            jellyfin.browse.titles.add(FakeTitle("other", "Another Collection", "lib-box", "BoxSet"))
            jellyfin.browse.titles.add(FakeTitle("m1", "One", "box", year = 2020))
            jellyfin.browse.titles.add(FakeTitle("m2", "Two", "box", year = 2021))
            jellyfin.browse.titles.add(FakeTitle("m3", "Three", "other", year = 2022))
            jellyfin.browse.titles.add(FakeTitle("solo", "Alone", "lib-movies", year = 2019))
            assertEquals(listOf("box"), before.collectionsOf("m2").map { it.id })
            assertEquals(listOf("other"), before.collectionsOf("m3").map { it.id })
            assertEquals(emptyList<BrowseItem>(), before.collectionsOf("solo"))
            val asked = jellyfin.requests.map { it.url.encodedPath }
            assertTrue("the route that does not exist is not asked for", asked.none { it.endsWith("/Collections") })
        }
}
