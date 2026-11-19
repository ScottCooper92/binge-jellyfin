package com.cooper.homelab.ephyra.demo

import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.JellyfinItemsRepository
import com.cooper.homelab.ephyra.browse.JellyfinLatestRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.browse.JellyfinNextUpRepository
import com.cooper.homelab.ephyra.browse.JellyfinResumeRepository
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.SortField
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
}
