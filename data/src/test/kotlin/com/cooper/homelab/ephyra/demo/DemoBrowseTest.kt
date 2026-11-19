package com.cooper.homelab.ephyra.demo

import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.JellyfinCollectionRepository
import com.cooper.homelab.ephyra.browse.JellyfinItemsRepository
import com.cooper.homelab.ephyra.browse.JellyfinLatestRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.browse.JellyfinNextUpRepository
import com.cooper.homelab.ephyra.browse.JellyfinPersonRepository
import com.cooper.homelab.ephyra.browse.JellyfinResumeRepository
import com.cooper.homelab.ephyra.browse.JellyfinSearchRepository
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.NextEpisode
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.SortField
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoBrowseTest {
    private val client =
        JellyfinClient(
            OkHttpClient.Builder().addInterceptor(DemoTransport()).build(),
            DEMO_ORIGIN.toHttpUrl(),
            ClientIdentity("Ephyra", "Test", "device-1", "1.0"),
            Dispatchers.Unconfined,
            DEMO_TOKEN,
            DiscardLogger,
        )
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 6))
    private val libraries = JellyfinLibraryRepository(client, "demo-user", profile)

    private suspend fun library(kind: LibraryKind) = libraries.views().first { it.kind == kind }

    @Test
    fun `the demo's movies and series list, sort and filter like a server's`() =
        runBlocking {
            val items = JellyfinItemsRepository(client, "demo-user")
            val movies = library(LibraryKind.Movies)
            val all = items.items(movies, withTotal = true)
            assertEquals(6, all.total)
            assertTrue(all.items.all { it.kind == ItemKind.Movie })
            assertEquals(
                listOf(2023, 2022, 2021),
                items.items(movies, ItemSort(SortField.Year, ascending = false), limit = 3).items.map { it.year },
            )
            assertEquals(listOf("Paper Boats"), items.items(movies, filters = ItemFilters(favouritesOnly = true)).items.map { it.name })
            assertTrue(items.filterChoices(movies).genres.contains("Drama"))
            val series = items.items(library(LibraryKind.TvShows), withTotal = true)
            assertEquals(3, series.total)
            assertTrue(series.items.all { it.kind == ItemKind.Series })
        }

    @Test
    fun `an item read with no parent still answers empty, so the library index stays empty`() =
        runBlocking {
            val answer = client.get("Items", mapOf("hasTmdbId" to "true", "recursive" to "true", "includeItemTypes" to "Movie,Series"))
            assertEquals(
                "[]",
                answer
                    .toString()
                    .substringAfter("\"Items\":")
                    .substringBefore(",\"")
                    .take(2),
            )
        }

    @Test
    fun `the home rows have titles`() =
        runBlocking {
            val resume = JellyfinResumeRepository(client, "demo-user", profile).resume().items
            assertEquals(listOf("The Long Quiet", "Episode 1"), resume.map { it.name })
            val next = JellyfinNextUpRepository(client, "demo-user").nextUp().items
            assertEquals(listOf("demo-ep-1-3"), next.map { it.id })
            val latest = JellyfinLatestRepository(client, "demo-user", profile)
            assertEquals("The Long Quiet", latest.latest(library(LibraryKind.Movies), limit = 1).single().name)
            // A TV library's latest is its episodes folded into their series, newest first.
            assertEquals(listOf("Harbour Lights", "Second Shift"), latest.latest(library(LibraryKind.TvShows)).map { it.name })
        }

    @Test
    fun `an older profile reaches the same rows`() =
        runBlocking {
            val old = JellyfinServerProfile(JellyfinVersion(10, 8, 13))
            assertEquals(2, JellyfinResumeRepository(client, "demo-user", old).resume().items.size)
            assertEquals(6, JellyfinLatestRepository(client, "demo-user", old).latest(library(LibraryKind.Movies)).size)
        }

    @Test
    fun `the demo searches titles and people by name`() =
        runBlocking {
            val search = JellyfinSearchRepository(client, "demo-user", profile)
            assertEquals(listOf("demo-movie-1"), search.titles("long QUIET").items.map { it.id })
            assertTrue(search.titles("zzz").items.isEmpty())
            assertEquals(listOf("demo-person-1"), search.people("rivers").items.map { it.id })
            assertTrue(search.people("zzz").items.isEmpty())
        }

    @Test
    fun `a demo title opens with a cast, a version and chapters, on either route`() =
        runBlocking {
            listOf(profile, JellyfinServerProfile(JellyfinVersion(10, 8, 13))).forEach { each ->
                val detail = JellyfinTitleRepository(client, "demo-user", each).title("demo-movie-1")!!
                assertEquals("The Long Quiet", detail.item.name)
                assertEquals(listOf(PersonKind.Actor, PersonKind.Director), detail.people.map { it.kind })
                assertEquals(1, detail.versions.size)
                assertEquals(
                    1920,
                    detail.versions
                        .single()
                        .video
                        ?.width,
                )
                assertEquals(2, detail.chapters.size)
            }
        }

    @Test
    fun `a demo series has seasons and episodes, and a next episode`() =
        runBlocking {
            val titles = JellyfinTitleRepository(client, "demo-user", profile)
            val season = titles.seasons("demo-series-1").single()
            assertEquals(1, season.seasonNumber)
            assertEquals(3, season.childCount)
            assertEquals(3, titles.episodes("demo-series-1", season.id).size)
            assertEquals("demo-ep-1-3", (titles.nextEpisode("demo-series-1") as NextEpisode.Next).episode.id)
            assertEquals("demo-ep-3-1", (titles.nextEpisode("demo-series-3") as NextEpisode.Resume).episode.id)
            assertEquals(NextEpisode.None, titles.nextEpisode("demo-series-2"))
        }

    @Test
    fun `the demo keeps played and favourite in memory, on either route`() =
        runBlocking {
            listOf(profile, JellyfinServerProfile(JellyfinVersion(10, 8, 13))).forEach { each ->
                val data = JellyfinUserDataRepository(client, "demo-user", each, emptyFlow())
                val movies = library(LibraryKind.Movies)

                suspend fun played() =
                    JellyfinItemsRepository(client, "demo-user")
                        .items(movies)
                        .items
                        .first { it.id == "demo-movie-1" }
                        .userData
                assertEquals(true, data.setPlayed("demo-movie-1", true).played)
                assertEquals(0L, data.setPlayed("demo-movie-1", true).positionTicks)
                assertEquals(true, played().played)
                assertEquals(true, data.setFavourite("demo-movie-1", true).favourite)
                assertEquals(true, played().favourite)
                assertEquals(false, data.setPlayed("demo-movie-1", false).played)
                assertEquals(false, data.setFavourite("demo-movie-1", false).favourite)
            }
        }

    @Test
    fun `the demo has similar titles, a person with titles, and no collections`() =
        runBlocking {
            val latest = JellyfinServerProfile(JellyfinVersion(12, 4, 0))
            val similar = JellyfinTitleRepository(client, "demo-user", latest).similar("demo-movie-1")
            assertTrue(similar.isNotEmpty() && similar.none { it.id == "demo-movie-1" })
            val people = JellyfinPersonRepository(client, "demo-user", latest)
            assertEquals("Sam Rivers", people.person("demo-person-1")!!.name)
            assertTrue(people.titlesWith("demo-person-1").items.isNotEmpty())
            assertTrue(JellyfinCollectionRepository(client, "demo-user", latest).collectionsOf("demo-movie-1")!!.isEmpty())
        }
}
