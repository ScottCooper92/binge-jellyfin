package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        assertTrue(client.authorization().contains("Device=\"Pixel%20%2210%22\""))
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
            assertEquals(CurrentUser("Claude", isAdministrator = false), client.withToken("token-1").me())
        }

    @Test
    fun `a server-relative URL is resolved against the base`() {
        assertEquals("${jellyfin.baseUrl}/videos/a.m3u8?x=1", client.resolve("/videos/a.m3u8?x=1"))
    }

    @Test
    fun `cancelling a call stops it instead of waiting for the server`() =
        runBlocking {
            jellyfin.slow += "/Users/Me"
            val call = async(Dispatchers.IO, CoroutineStart.UNDISPATCHED) { client.withToken("abc").me() }
            while (jellyfin.hits("/Users/Me") == 0) Thread.sleep(10)
            withTimeout(5_000) { call.cancelAndJoin() }
            assertTrue(call.isCancelled)
        }

    @Test
    fun `a device model with non-ASCII characters or a comma is encoded, and the request is sent`() =
        runBlocking {
            val odd = JellyfinClient(OkHttpClient(), jellyfin.baseUrl.toHttpUrl(), identity.copy(device = "Ünï Phone, Pro"), Dispatchers.IO)
            assertTrue(odd.authorization().contains("Device=\"%C3%9Cn%C3%AF%20Phone%2C%20Pro\""))
            assertEquals("truenas", odd.publicInfo().serverName)
            val sent =
                jellyfin.requests
                    .last()
                    .headers["Authorization"]
                    .orEmpty()
            assertTrue(sent.contains("%C3%9Cn%C3%AF%20Phone%2C%20Pro"))
        }

    @Test
    fun `an answer that is not JSON is an IOException, not an IllegalArgumentException`() {
        jellyfin.notJson += "/System/Info/Public"
        val failure = assertThrows(JellyfinUnreadableAnswer::class.java) { runBlocking { client.publicInfo() } }
        assertTrue(failure is java.io.IOException)
        assertTrue(failure.cause is kotlinx.serialization.SerializationException)
    }

    @Test
    fun `approving a code the server waits on works, and any other is a 404`() =
        runBlocking {
            val signed = client.withToken("token-1")
            signed.authorizeQuickConnect("654321")
            val wrong = assertThrows(JellyfinHttpException::class.java) { runBlocking { signed.authorizeQuickConnect("000000") } }
            assertEquals(404, wrong.code)
            jellyfin.administrator = true
            assertTrue(signed.me().isAdministrator)
        }
}
