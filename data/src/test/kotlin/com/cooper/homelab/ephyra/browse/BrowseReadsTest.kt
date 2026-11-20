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

class BrowseReadsTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Ephyra", "Pixel", "device-1", "1.0")
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO, logger = DiscardLogger)
            .withToken("token-1")
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 6))
    private val libraries = JellyfinLibraryRepository(client, "user-1", profile)
    private val items = JellyfinItemsRepository(client, "user-1")
    private val movies = LibraryView("lib-movies", "Movies", LibraryKind.Movies)

    @After
    fun close() = jellyfin.close()

    private fun title(
        id: String,
        name: String,
        year: Int? = null,
        genres: List<String> = emptyList(),
        rating: Double? = null,
        played: Boolean = false,
        favourite: Boolean = false,
        added: Int = 0,
        official: String? = null,
    ) = jellyfin.browse.titles.add(FakeTitle(id, name, "lib-movies", "Movie", year, genres, official, rating, played, favourite, added))

    private fun names(page: Page<BrowseItem>) = page.items.map { it.name }

    @Test
    fun `a collections view is BoxSets, a view with no type is Mixed, and music is Other`() =
        runBlocking {
            jellyfin.userViews =
                """[{"Id":"lib-sets","Name":"Collections","CollectionType":"boxsets"},""" +
                """{"Id":"lib-mixed","Name":"Everything"},{"Id":"lib-music","Name":"Music","CollectionType":"music"}]"""
            val views = libraries.views()
            assertEquals(listOf(LibraryKind.BoxSets, LibraryKind.Mixed, LibraryKind.Other), views.map { it.kind })
            assertEquals("BoxSet", LibraryKind.BoxSets.itemTypes())
            assertEquals("Movie,Series", LibraryKind.Mixed.itemTypes())
        }

    @Test
    fun `a collection type is matched ignoring case, and blank is Mixed`() {
        assertEquals(LibraryKind.BoxSets, LibraryKind.fromServer("BoxSets"))
        assertEquals(LibraryKind.Mixed, LibraryKind.fromServer(null))
        assertEquals(LibraryKind.Mixed, LibraryKind.fromServer(""))
        assertEquals(LibraryKind.Other, LibraryKind.fromServer("livetv"))
    }

    @Test
    fun `views list each library with its kind, and an unknown kind is Other`() =
        runBlocking {
            val views = libraries.views()
            assertEquals(listOf("Movies", "Shows", "Home videos"), views.map { it.name })
            assertEquals(listOf(LibraryKind.Movies, LibraryKind.TvShows, LibraryKind.Other), views.map { it.kind })
            assertEquals("lib-movies", views.first().id)
        }

    @Test
    fun `a library carries its Primary, Thumb and first backdrop tags, and one with only a Primary has no others`() =
        runBlocking {
            val (movies, shows, home) = libraries.views()
            assertEquals(Triple("lp", "lt", "lb"), Triple(movies.primaryImageTag, movies.thumbImageTag, movies.backdropImageTag))
            assertEquals(Triple("sp", null, null), Triple(shows.primaryImageTag, shows.thumbImageTag, shows.backdropImageTag))
            assertEquals(Triple(null, null, null), Triple(home.primaryImageTag, home.thumbImageTag, home.backdropImageTag))
        }

    @Test
    fun `an older server is asked for views the old way`() =
        runBlocking {
            JellyfinLibraryRepository(client, "user-1", JellyfinServerProfile(JellyfinVersion(10, 8, 13))).views()
            assertTrue(jellyfin.requests.any { it.url.encodedPath == "/Users/user-1/Views" })
        }

    @Test
    fun `an empty library is an empty page`() =
        runBlocking {
            val page = items.items(movies, withTotal = true)
            assertTrue(page.items.isEmpty())
            assertEquals(0, page.total)
            assertNull(page.nextStart(60))
        }

    @Test
    fun `a card read names what it wants, and asks for no count unless told`() =
        runBlocking {
            title("m1", "Alien", year = 1979)
            val page = items.items(movies, startIndex = 0, limit = 10)
            assertEquals(listOf("Alien"), names(page))
            assertNull(page.total)
            val sent =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            assertEquals("user-1", sent.queryParameter("userId"))
            assertEquals("lib-movies", sent.queryParameter("parentId"))
            assertEquals("Movie", sent.queryParameter("includeItemTypes"))
            assertEquals("true", sent.queryParameter("recursive"))
            assertEquals("true", sent.queryParameter("enableUserData"))
            assertEquals("PrimaryImageAspectRatio", sent.queryParameter("fields"))
            assertEquals("Primary,Thumb,Backdrop", sent.queryParameter("enableImageTypes"))
            assertEquals("1", sent.queryParameter("imageTypeLimit"))
            assertEquals("false", sent.queryParameter("enableTotalRecordCount"))
            assertEquals("SortName", sent.queryParameter("sortBy"))
            assertEquals("Ascending", sent.queryParameter("sortOrder"))
            assertEquals("10", sent.queryParameter("limit"))
            assertFalse(sent.toString().contains("token-1"))
        }

    @Test
    fun `a card carries what it shows`() =
        runBlocking {
            title("m1", "Alien", year = 1979, rating = 8.5, played = true, favourite = true, official = "R")
            val item = items.items(movies).items.single()
            assertEquals(ItemKind.Movie, item.kind)
            assertEquals(1979, item.year)
            assertEquals(8.5, item.communityRating)
            assertEquals("R", item.officialRating)
            assertEquals(72000000000L, item.runTimeTicks)
            assertEquals(mapOf(ImageKind.Primary to "p-m1", ImageKind.Backdrop to "b-m1"), item.imageTags)
            assertEquals(0.6667, item.primaryAspectRatio!!, 0.0001)
            assertTrue(item.userData.played)
            assertTrue(item.userData.favourite)
        }

    @Test
    fun `paging walks the list, with the total when asked, and past the end is empty`() =
        runBlocking {
            listOf("A", "B", "C", "D", "E").forEachIndexed { i, n -> title("m$i", n) }
            val first = items.items(movies, startIndex = 0, limit = 2, withTotal = true)
            assertEquals(listOf("A", "B"), names(first))
            assertEquals(5, first.total)
            assertEquals(2, first.nextStart(2))
            val last = items.items(movies, startIndex = 4, limit = 2, withTotal = true)
            assertEquals(listOf("E"), names(last))
            assertNull(last.nextStart(2))
            val past = items.items(movies, startIndex = 10, limit = 2, withTotal = true)
            assertTrue(past.items.isEmpty())
            assertEquals(5, past.total)
            assertNull(past.nextStart(2))
        }

    @Test
    fun `without a total a full page may have more, and a short one is the last`() =
        runBlocking {
            listOf("A", "B", "C").forEachIndexed { i, n -> title("m$i", n) }
            assertEquals(2, items.items(movies, limit = 2).nextStart(2))
            assertNull(items.items(movies, startIndex = 2, limit = 2).nextStart(2))
        }

    @Test
    fun `sort goes to the server with a name tie-break, and descending is asked for`() =
        runBlocking {
            title("m1", "Alien", year = 1979, rating = 8.5, added = 1)
            title("m2", "Brazil", year = 1985, rating = 7.9, added = 3)
            title("m3", "Cube", year = 1997, rating = 8.5, added = 2)
            assertEquals(
                listOf("Cube", "Brazil", "Alien"),
                names(items.items(movies, ItemSort(SortField.Year, ascending = false))),
            )
            assertEquals(
                "ProductionYear,SortName",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("sortBy"),
            )
            assertEquals(
                "Descending",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("sortOrder"),
            )
            assertEquals(listOf("Alien", "Cube", "Brazil"), names(items.items(movies, ItemSort(SortField.Rating, ascending = false))))
            assertEquals(listOf("Brazil", "Cube", "Alien"), names(items.items(movies, ItemSort(SortField.DateAdded, ascending = false))))
        }

    @Test
    fun `filters narrow by genre, year, rating, watched and favourite`() =
        runBlocking {
            title("m1", "Alien", year = 1979, genres = listOf("Horror", "Sci-Fi"), official = "R", played = true)
            title("m2", "Brazil", year = 1985, genres = listOf("Sci-Fi"), official = "15", favourite = true)
            title("m3", "Cube", year = 1997, genres = listOf("Horror"), official = "18")
            assertEquals(listOf("Alien", "Brazil"), names(items.items(movies, filters = ItemFilters(genres = setOf("Sci-Fi")))))
            assertEquals(
                listOf("Alien", "Brazil", "Cube"),
                names(items.items(movies, filters = ItemFilters(genres = setOf("Sci-Fi", "Horror")))),
            )
            assertEquals(
                "Sci-Fi|Horror",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("genres"),
            )
            assertEquals(listOf("Brazil", "Cube"), names(items.items(movies, filters = ItemFilters(years = setOf(1997, 1985)))))
            assertEquals(
                "1985,1997",
                jellyfin.browse.itemRequests
                    .last()
                    .url
                    .queryParameter("years"),
            )
            assertEquals(listOf("Cube"), names(items.items(movies, filters = ItemFilters(officialRatings = setOf("18")))))
            assertEquals(listOf("Alien"), names(items.items(movies, filters = ItemFilters(played = true))))
            assertEquals(listOf("Brazil", "Cube"), names(items.items(movies, filters = ItemFilters(played = false))))
            assertEquals(listOf("Brazil"), names(items.items(movies, filters = ItemFilters(favouritesOnly = true))))
        }

    @Test
    fun `no filter sends no filter parameters`() =
        runBlocking {
            items.items(movies)
            val sent =
                jellyfin.browse.itemRequests
                    .last()
                    .url
            listOf("genres", "years", "officialRatings", "isPlayed", "isFavorite").forEach { assertNull(it, sent.queryParameter(it)) }
            assertTrue(ItemFilters().isEmpty)
        }

    @Test
    fun `filter choices are what the library has, genres by name and years newest first`() =
        runBlocking {
            title("m1", "Alien", year = 1979, genres = listOf("sci-fi"), official = "R")
            title("m2", "Brazil", year = 1985, genres = listOf("Horror"), official = "15")
            val choices = items.filterChoices(movies)
            assertEquals(listOf("Horror", "sci-fi"), choices.genres)
            assertEquals(listOf(1985, 1979), choices.years)
            assertEquals(listOf("15", "R"), choices.officialRatings)
        }

    @Test
    fun `a failed page throws the transport's error, and an unreadable one does too`() {
        jellyfin.status["/Items"] = 500
        assertEquals(500, assertThrows(JellyfinHttpException::class.java) { runBlocking { items.items(movies) } }.code)
        jellyfin.status.clear()
        jellyfin.notJson += "/Items"
        assertThrows(JellyfinUnreadableAnswer::class.java) { runBlocking { items.items(movies) } }
        jellyfin.status["/UserViews"] = 401
        assertEquals(401, assertThrows(JellyfinHttpException::class.java) { runBlocking { libraries.views() } }.code)
    }

    @Test
    fun `an item type this app does not know is Other, and an entry with no id is dropped`() {
        val page =
            BrowseJson
                .decodeFromString(
                    ItemsWire.serializer(),
                    """{"Items":[{"Id":"x","Name":"Mix","Type":"Trailer","Extra":1},{"Name":"No id"}]}""",
                ).toPage(0, withTotal = false)
        assertEquals(listOf(ItemKind.Other), page.items.map { it.kind })
        assertEquals(ItemKind.Series, ItemKind.fromServer("series"))
    }

    @Test
    fun `picture URLs are sized, tagged and carry no token`() {
        val urls = ImageUrls(client)
        val item = BrowseItem("m1", "Alien", ItemKind.Movie, imageTags = mapOf(ImageKind.Primary to "abc"))
        val url = urls.of(item, ImageKind.Primary, maxWidth = 300)!!.toHttpUrl()
        assertEquals("/Items/m1/Images/Primary", url.encodedPath)
        assertEquals("abc", url.queryParameter("tag"))
        assertEquals("300", url.queryParameter("maxWidth"))
        assertNull(url.queryParameter("maxHeight"))
        assertFalse(url.toString().contains("token-1"))
        assertNull(urls.of(item, ImageKind.Backdrop, 300))
        assertEquals("200", urls.of("m1", ImageKind.Logo, "t", 100, 200).toHttpUrl().queryParameter("maxHeight"))
        assertEquals("/Items/m1/Images/Logo", urls.of("m1", ImageKind.Logo, "t", 100).toHttpUrl().encodedPath)
    }
}
