package com.cooper.homelab.ephyra.demo

import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.JellyfinCollectionRepository
import com.cooper.homelab.ephyra.browse.JellyfinItemsRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.browse.JellyfinPersonRepository
import com.cooper.homelab.ephyra.browse.JellyfinSearchRepository
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.NextEpisode
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the demo gives every screen the client has: a season of ten and a special, two versions of a film, a film under a minute,
 * box sets (one film in two), a person with more credits than a page, one with no picture and one with no credits, a Collections
 * view and a mixed one, and more films than a grid page. Each read is the client's own, against the demo transport.
 */
class DemoClientDataTest {
    private val client =
        JellyfinClient(
            OkHttpClient.Builder().addInterceptor(DemoTransport()).build(),
            DEMO_ORIGIN.toHttpUrl(),
            ClientIdentity("Ephyra", "Test", "device-1", "1.0"),
            Dispatchers.Unconfined,
            DEMO_TOKEN,
            DiscardLogger,
        )
    private val before12 = JellyfinServerProfile(JellyfinVersion(10, 11, 4))
    private val from12 = JellyfinServerProfile(JellyfinVersion(12, 4, 0))
    private val titles = JellyfinTitleRepository(client, "demo-user", before12)
    private val items = JellyfinItemsRepository(client, "demo-user")
    private val people = JellyfinPersonRepository(client, "demo-user", before12)

    private suspend fun view(kind: LibraryKind) = JellyfinLibraryRepository(client, "demo-user", before12).views().first { it.kind == kind }

    @Test
    fun `a series has specials, a season of ten and a second season, in the server's order`() =
        runBlocking {
            val seasons = titles.seasons("demo-series-4")
            assertEquals(listOf(0, 1, 2), seasons.map { it.seasonNumber })
            assertEquals(listOf(2, 10, 4), seasons.map { it.childCount })
            assertEquals(10, titles.episodes("demo-series-4", "demo-series-4-s1").size)
            assertEquals(4, titles.episodes("demo-series-4", "demo-series-4-s2").size)
        }

    @Test
    fun `a special with no number comes after the numbered one`() =
        runBlocking {
            val specials = titles.episodes("demo-series-4", "demo-series-4-s0")
            assertEquals(listOf(1, null), specials.map { it.episodeNumber })
            assertEquals("Outtakes", specials.last().name)
        }

    @Test
    fun `a season has watched, part watched and unwatched episodes, so the series resumes the part watched one`() =
        runBlocking {
            val season = titles.episodes("demo-series-4", "demo-series-4-s1")
            assertEquals(4, season.count { it.userData.played })
            assertTrue(season[4].userData.positionTicks > 0)
            assertTrue(season.drop(5).all { !it.userData.played && it.userData.positionTicks == 0L })
            assertEquals("demo-ep-4-s1-5", (titles.nextEpisode("demo-series-4") as NextEpisode.Resume).episode.id)
            // The first series has no part watched episode, so it plays the first unwatched one.
            assertEquals("demo-ep-1-3", (titles.nextEpisode("demo-series-1") as NextEpisode.Next).episode.id)
        }

    @Test
    fun `a season and an episode open as titles of their own`() =
        runBlocking {
            val season = titles.title("demo-series-4-s1")!!
            assertEquals(ItemKind.Season, season.item.kind)
            assertTrue(season.versions.isEmpty())
            val episode = titles.title("demo-ep-4-s1-5")!!
            assertEquals(ItemKind.Episode, episode.item.kind)
            assertEquals(1, episode.versions.size)
        }

    @Test
    fun `a film has two versions to choose between, and the second is a different file`() =
        runBlocking {
            val film = titles.title("demo-movie-7")!!
            assertEquals(2, film.versions.size)
            assertEquals(listOf(1080, 2160), film.versions.map { it.video?.height })
            assertEquals(1, titles.title("demo-movie-1")!!.versions.size)
        }

    @Test
    fun `a film can run under a minute, and a film can have a resume point`() =
        runBlocking {
            val short = titles.title("demo-movie-9")!!.item
            assertTrue(short.runTimeTicks!! < 600_000_000L)
            assertTrue(
                titles
                    .title("demo-movie-1")!!
                    .item.userData.positionTicks > 0,
            )
        }

    @Test
    fun `the Collections view lists box sets, none with a backdrop and one with no poster`() =
        runBlocking {
            val boxes = items.items(view(LibraryKind.BoxSets), withTotal = true)
            assertEquals(listOf("The Quiet Collection", "Late Night Trilogy"), boxes.items.map { it.name }.sortedByDescending { it.length })
            assertEquals(2, boxes.total)
            assertTrue(boxes.items.all { it.kind == ItemKind.BoxSet })
            assertTrue(boxes.items.all { ImageKind.Backdrop !in it.imageTags })
            assertEquals(listOf("The Quiet Collection"), boxes.items.filter { ImageKind.Primary in it.imageTags }.map { it.name })
        }

    @Test
    fun `a box set with no poster has its first part's poster to show`() =
        runBlocking {
            val box = items.items(view(LibraryKind.BoxSets), withTotal = true).items.first { ImageKind.Primary !in it.imageTags }
            assertNotNull(items.firstPoster(box))
        }

    @Test
    fun `a box set has three or more films, and its page reads them as its parts`() =
        runBlocking {
            val collections = JellyfinCollectionRepository(client, "demo-user", before12)
            assertEquals(4, collections.parts("demo-box-1").size)
            assertEquals(listOf("demo-movie-4", "demo-movie-5", "demo-movie-6"), collections.parts("demo-box-2").map { it.id }.sorted())
            assertEquals(ItemKind.BoxSet, titles.title("demo-box-1")!!.item.kind)
        }

    @Test
    fun `a film in two box sets is found in both, before 12 by scanning and from 12 by the route`() =
        runBlocking {
            val both = listOf("demo-box-1", "demo-box-2")
            assertEquals(
                both,
                JellyfinCollectionRepository(client, "demo-user", before12).collectionsOf("demo-movie-4").map { it.id }.sorted(),
            )
            assertEquals(
                both,
                JellyfinCollectionRepository(client, "demo-user", from12).collectionsOf("demo-movie-4").map { it.id }.sorted(),
            )
            assertEquals(
                emptyList<String>(),
                JellyfinCollectionRepository(client, "demo-user", before12).collectionsOf("demo-movie-8").map { it.id },
            )
        }

    @Test
    fun `a person has more credits than a page, so there is a second one`() =
        runBlocking {
            val first = people.titlesWith(SAM_RIVERS, startIndex = 0, limit = 60)
            assertEquals(60, first.items.size)
            val second = people.titlesWith(SAM_RIVERS, startIndex = 60, limit = 60)
            assertTrue(second.items.isNotEmpty())
            assertTrue((first.items + second.items).all { it.kind == ItemKind.Movie || it.kind == ItemKind.Series })
        }

    @Test
    fun `a person with no picture and a person with no credits are on the server`() =
        runBlocking {
            assertNull(people.person(ROBIN_HALE)!!.imageTag)
            assertTrue(people.titlesWith(ROBIN_HALE).items.isNotEmpty())
            assertEquals("p-$SAM_RIVERS", people.person(SAM_RIVERS)!!.imageTag)
            assertEquals(emptyList<String>(), people.titlesWith(QUINN_ASH).items.map { it.id })
            assertEquals("Quinn Ash", people.person(QUINN_ASH)!!.name)
        }

    @Test
    fun `every person a title lists has that title in their credits`() =
        runBlocking {
            listOf("demo-movie-1", "demo-movie-7", "demo-series-4", "demo-series-1", "demo-film-5", "demo-movie-4").forEach { id ->
                titles.title(id)!!.people.forEach { person ->
                    val credits = people.titlesWith(person.id, limit = 200).items.map { it.id }
                    assertTrue("$id is in ${person.name}'s credits", id in credits)
                }
            }
            // A title that credits no one lists no one, and the director is on the titles that credit him.
            assertTrue(titles.title("demo-movie-4")!!.people.isEmpty())
            assertTrue(people.titlesWith(ALEX_MOOR).items.isNotEmpty())
        }

    @Test
    fun `a cast row has a person with a picture and one without`() =
        runBlocking {
            val cast = titles.title("demo-movie-7")!!.people
            assertTrue(cast.any { it.name == "Sam Rivers" && it.imageTag != null })
            assertTrue(cast.any { it.name == "Robin Hale" && it.imageTag == null })
        }

    @Test
    fun `the libraries are Movies, TV shows, Collections, a mixed one, and Music`() =
        runBlocking {
            val views = JellyfinLibraryRepository(client, "demo-user", before12).views()
            assertEquals(listOf("Movies", "TV shows", "Collections", "Mixed", "Music"), views.map { it.name })
            assertEquals(
                listOf(LibraryKind.Movies, LibraryKind.TvShows, LibraryKind.BoxSets, LibraryKind.Mixed, LibraryKind.Other),
                views.map { it.kind },
            )
        }

    @Test
    fun `a mixed library lists films and series together`() =
        runBlocking {
            val mixed = JellyfinLibraryRepository(client, "demo-user", before12).views().first { it.name == "Mixed" }
            val kinds =
                items
                    .items(mixed, limit = 200)
                    .items
                    .map { it.kind }
                    .toSet()
            assertEquals(setOf(ItemKind.Movie, ItemKind.Series), kinds)
        }

    @Test
    fun `there are more films than a grid page, with filters that narrow the list`() =
        runBlocking {
            val movies = view(LibraryKind.Movies)
            val first = items.items(movies, withTotal = true)
            assertEquals(60, first.items.size)
            assertEquals(true, first.total!! > 60)
            assertTrue(items.items(movies, startIndex = 60).items.isNotEmpty())
            val choices = items.filterChoices(movies)
            assertTrue(choices.genres.size > 4 && choices.years.size > 10 && choices.officialRatings.size > 3)
            val comedies = items.items(movies, filters = ItemFilters(genres = setOf("Comedy")), withTotal = true)
            assertTrue(comedies.total!! in 1 until first.total!!)
            assertTrue(items.items(movies, filters = ItemFilters(played = true)).items.isNotEmpty())
        }

    @Test
    fun `search finds a film, an episode and a person`() =
        runBlocking {
            val search = JellyfinSearchRepository(client, "demo-user", before12)
            assertTrue(search.titles("Amber").items.any { it.kind == ItemKind.Movie })
            assertTrue(search.titles("Lamp").items.any { it.kind == ItemKind.Episode })
            assertEquals(listOf(ROBIN_HALE), search.people("hale").items.map { it.id })
            assertFalse(search.titles("zzz").items.any())
        }

    @Test
    fun `the library index still finds nothing, whatever the demo holds`() =
        runBlocking {
            val answer = client.get("Items", mapOf("hasTmdbId" to "true", "recursive" to "true", "includeItemTypes" to "Movie,Series"))
            assertTrue(answer.toString().contains("\"TotalRecordCount\":0"))
        }
}
