package com.cooper.binge.companion.jellyfin.demo

import com.cooper.binge.companion.jellyfin.server.ClientIdentity
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.authenticateByName
import com.cooper.binge.companion.jellyfin.server.itemCounts
import com.cooper.binge.companion.jellyfin.server.libraryPage
import com.cooper.binge.companion.jellyfin.server.me
import com.cooper.binge.companion.jellyfin.server.nowPlaying
import com.cooper.binge.companion.jellyfin.server.publicInfo
import com.cooper.binge.companion.jellyfin.server.serverInfo
import com.cooper.binge.companion.jellyfin.server.titlesWithoutTmdbId
import com.cooper.binge.companion.jellyfin.server.users
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTransportTest {
    private val http = OkHttpClient.Builder().addInterceptor(DemoTransport()).build()
    private val client =
        JellyfinClient(http, DEMO_ORIGIN.toHttpUrl(), ClientIdentity("Ephyra", "Test", "device-1", "1.0"), Dispatchers.Unconfined)

    @Test
    fun `the demo answers as a Jellyfin server and signs in with no password`() =
        runBlocking {
            val info = client.publicInfo()
            assertEquals(DEMO_SERVER_ID, info.serverId)
            val signedIn = client.authenticateByName(DEMO_USER_NAME, "")
            assertEquals(DEMO_TOKEN, signedIn.token)
            assertEquals(DEMO_USER_ID, signedIn.userId)
            val me = client.withToken(signedIn.token).me()
            assertTrue(me.isAdministrator)
        }

    @Test
    fun `the home card reads its figures from the demo`() =
        runBlocking {
            val signedIn = client.withToken(DEMO_TOKEN)
            assertEquals("Arm64", signedIn.serverInfo().architecture)
            assertEquals(3, signedIn.users().size)
            assertEquals(2, signedIn.nowPlaying().size)
            assertEquals(128, signedIn.itemCounts(DEMO_USER_ID).movies)
        }

    @Test
    fun `the demo has no titles to tell Binge about`() =
        runBlocking {
            val signedIn = client.withToken(DEMO_TOKEN)
            val (items, total) = signedIn.libraryPage(DEMO_USER_ID, startIndex = 0, limit = 500)
            assertTrue(items.isEmpty())
            assertEquals(0, total)
            assertEquals(0, signedIn.titlesWithoutTmdbId(DEMO_USER_ID))
        }

    @Test
    fun `a call the demo does not serve says so`() {
        val failure = assertThrows(JellyfinHttpException::class.java) { runBlocking { client.withToken(DEMO_TOKEN).get("Not/Served") } }
        assertEquals(501, failure.code)
    }

    @Test
    fun `only the demo's host is answered`() {
        assertTrue(DEMO_ORIGIN.isDemoOrigin())
        assertTrue("$DEMO_ORIGIN/".isDemoOrigin())
        assertFalse("https://jf.example.com".isDemoOrigin())
    }
}
