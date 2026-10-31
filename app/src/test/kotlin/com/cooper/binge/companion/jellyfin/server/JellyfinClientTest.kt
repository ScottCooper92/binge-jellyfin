package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.FakeJellyfin
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

class JellyfinClientTest {
    private val jellyfin = FakeJellyfin()
    private val identity = ClientIdentity("Binge Jellyfin", "Pixel \"10\"", "device-1", "1.0")
    private val client = JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity, Dispatchers.IO)

    @After
    fun close() = jellyfin.close()

    @Test
    fun `an address without a scheme is http, and junk is not an address`() {
        assertEquals("http://192.168.1.10:8096/", JellyfinClient.parseBaseUrl(" 192.168.1.10:8096/ ").toString())
        assertEquals("https://jf.example.com/", JellyfinClient.parseBaseUrl("https://jf.example.com").toString())
        assertNull(JellyfinClient.parseBaseUrl(""))
        assertNull(JellyfinClient.parseBaseUrl("http://"))
    }

    @Test
    fun `the header names the client and carries the token only once signed in`() {
        assertFalse(client.authorization().contains("Token"))
        assertTrue(client.authorization().contains("Device=\"Pixel 10\""))
        assertTrue(client.withToken("abc").authorization().endsWith("Token=\"abc\""))
    }

    @Test
    fun `public info and a password sign-in`() =
        runBlocking {
            assertEquals("truenas", client.publicInfo().serverName)
            val signedIn = client.authenticateByName("Claude", "right")
            assertEquals("token-1", signedIn.token)
            assertEquals("user-1", signedIn.userId)
            assertEquals("Claude", signedIn.userName)
            val wrong = assertThrows(JellyfinHttpException::class.java) { runBlocking { client.authenticateByName("Claude", "wrong") } }
            assertEquals(401, wrong.code)
        }

    @Test
    fun `Quick Connect from code to session`() =
        runBlocking {
            assertTrue(client.quickConnectEnabled())
            val start = client.quickConnectInitiate()
            assertEquals("123456", start.code)
            assertFalse(client.quickConnectAuthenticated(start.secret))
            jellyfin.quickConnectApproved = true
            assertTrue(client.quickConnectAuthenticated(start.secret))
            assertEquals("token-1", client.authenticateWithQuickConnect(start.secret).token)
            assertEquals("Claude", client.withToken("token-1").me())
        }

    @Test
    fun `a server-relative URL is resolved against the base`() {
        assertEquals("${jellyfin.baseUrl}/videos/a.m3u8?x=1", client.resolve("/videos/a.m3u8?x=1"))
    }
}
