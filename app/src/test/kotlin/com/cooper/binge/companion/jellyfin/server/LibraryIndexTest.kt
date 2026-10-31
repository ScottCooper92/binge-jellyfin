package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.FakeItem
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIndexTest {
    private val jellyfin = FakeJellyfin()
    private val client =
        JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), ClientIdentity("c", "d", "id", "1"), Dispatchers.IO, token = "token-1")
    private var now = 0L
    private val index = LibraryIndex(now = { now })

    @After
    fun close() = jellyfin.close()

    private fun requestsForItems() = jellyfin.hits("/Items")

    @Test
    fun `pages in every titled movie and series, and serves later lookups from memory`() =
        runBlocking {
            repeat(1_200) { jellyfin.items += FakeItem("m$it", "Movie $it", "Movie", tmdbId = it + 1) }
            jellyfin.items += FakeItem("s1", "Show", "Series", tmdbId = 1)
            assertEquals("m0", index.find(client, "user-1", IndexKey(IndexType.MOVIE, 1))?.id)
            assertEquals("s1", index.find(client, "user-1", IndexKey(IndexType.SERIES, 1))?.id)
            assertEquals(1_201, index.size)
            assertEquals(3, requestsForItems())
            assertEquals(IndexKey(IndexType.SERIES, 1), index.keyOf(client, "user-1", "s1"))
        }

    @Test
    fun `a miss refreshes once the index is a minute old, and a fresh one answers from memory`() =
        runBlocking {
            assertNull(index.find(client, "user-1", IndexKey(IndexType.MOVIE, 9)))
            val built = requestsForItems()
            jellyfin.items += FakeItem("new", "New", "Movie", tmdbId = 9)
            now = 30_000L
            assertNull(index.find(client, "user-1", IndexKey(IndexType.MOVIE, 9)))
            assertEquals(built, requestsForItems())
            now = 61_000L
            assertEquals("new", index.find(client, "user-1", IndexKey(IndexType.MOVIE, 9))?.id)
        }

    @Test
    fun `another user, a refresh or a clear rebuilds it`() =
        runBlocking {
            jellyfin.items += FakeItem("m", "M", "Movie", tmdbId = 5)
            index.find(client, "user-1", IndexKey(IndexType.MOVIE, 5))
            index.find(client, "user-2", IndexKey(IndexType.MOVIE, 5))
            assertEquals(2, requestsForItems())
            index.refresh(client, "user-2")
            assertEquals(3, requestsForItems())
            index.clear()
            assertNull(index.size)
        }

    @Test
    fun `every library page is asked for as the signed-in user, so the server applies their access`() =
        runBlocking {
            jellyfin.items += FakeItem("m", "M", "Movie", tmdbId = 5)
            index.find(client, "user-1", IndexKey(IndexType.MOVIE, 5))
            val pages = jellyfin.requests.filter { it.url.encodedPath == "/Items" }
            assertTrue(pages.isNotEmpty())
            assertTrue(pages.all { it.url.queryParameter("userId") == "user-1" })
        }
}
