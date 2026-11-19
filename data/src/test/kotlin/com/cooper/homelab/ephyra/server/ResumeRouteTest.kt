package com.cooper.homelab.ephyra.server

import com.cooper.homelab.ephyra.FakeJellyfin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The LIBRARY contract's continue-watching read, on both generations of the route (10.8 and 10.9 and later). */
class ResumeRouteTest {
    private val jellyfin = FakeJellyfin()
    private val client =
        JellyfinClient(
            OkHttpClient(),
            jellyfin.baseUrl.toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1.0"),
            Dispatchers.IO,
            logger = DiscardLogger,
        ).withToken("token-1")

    @After
    fun close() = jellyfin.close()

    private fun paths() = jellyfin.requests.map { it.url.encodedPath }

    @Test
    fun `a 10_9 or later server is asked at the user items route`() =
        runBlocking {
            client.resumeItems("user-1", JellyfinServerProfile(JellyfinVersion(10, 11, 6)), 0, 20)
            assertTrue("/UserItems/Resume" in paths())
            assertEquals(
                "user-1",
                jellyfin.requests
                    .last { it.url.encodedPath == "/UserItems/Resume" }
                    .url
                    .queryParameter("userId"),
            )
        }

    @Test
    fun `a 10_8 server is asked at the old route, with the user in the path, and the answer reads the same`() =
        runBlocking {
            val page = client.resumeItems("user-1", JellyfinServerProfile(JellyfinVersion(10, 8, 13)), 0, 20)
            assertTrue("/Users/user-1/Items/Resume" in paths())
            assertFalse("/UserItems/Resume" in paths())
            assertEquals(client.resumeItems("user-1", JellyfinServerProfile(JellyfinVersion(10, 11, 6)), 0, 20).items, page.items)
        }

    @Test
    fun `a server whose version is unknown is taken to have the newer route`() =
        runBlocking {
            client.resumeItems("user-1", JellyfinServerProfile(null), 0, 20)
            assertTrue("/UserItems/Resume" in paths())
        }
}
