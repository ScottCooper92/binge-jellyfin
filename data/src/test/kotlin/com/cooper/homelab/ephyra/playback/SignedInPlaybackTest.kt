package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three seams bound to whichever session is saved when each call is made. */
class SignedInPlaybackTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Ephyra", "Pixel", "1", Dispatchers.IO, DiscardLogger)
    private val item = PlayItem("movie-1", PlayTitle("The Long Voyage"))
    private val profile = PlaybackDeviceProfile(videoCodecs = listOf("video/avc"), audioCodecs = listOf("audio/mp4a-latm"), maxWidth = 1920)

    @After
    fun close() {
        connection.close()
        jellyfin.close()
    }

    @Test
    fun `a source is minted, and its play reported, as the saved session`() =
        runBlocking {
            sessions.save(jellyfin.session())

            val minted = SignedInSourceMinter(connection).mint(item, profile, null, null)
            SignedInPlayReporter(connection).report(minted.report, PlayReport.STARTED, 0L)

            assertTrue(jellyfin.requests.any { it.method == "POST" && it.url.encodedPath == "/Items/movie-1/PlaybackInfo" })
            assertTrue(jellyfin.requests.any { it.method == "POST" && it.url.encodedPath == "/Sessions/Playing" })
        }

    @Test
    fun `the user's cap is what the signed in minter asks the server for`() =
        runBlocking {
            sessions.save(jellyfin.session())

            SignedInSourceMinter(connection) { 4_000_000L }.mint(item, profile.copy(maxBitrateBps = 40_000_000L), null, null)

            val body = jellyfin.lastBody("/Items/movie-1/PlaybackInfo")
            assertEquals(4_000_000L, body["MaxStreamingBitrate"]?.jsonPrimitive?.content?.toLong())
        }

    @Test
    fun `the next episode is asked for as the saved session, and a film has none`() =
        runBlocking {
            sessions.save(jellyfin.session())

            assertEquals(null, SignedInNextEpisodes(connection).after(item))
        }

    @Test
    fun `with no session each call is refused like a token the server refused`() =
        runBlocking {
            val minted = SignedInSourceMinter(connection)
            assertEquals(
                401,
                assertThrows(JellyfinHttpException::class.java) { runBlocking { minted.mint(item, profile, null, null) } }.code,
            )
            assertEquals(
                401,
                assertThrows(JellyfinHttpException::class.java) { runBlocking { SignedInNextEpisodes(connection).after(item) } }.code,
            )
        }
}
