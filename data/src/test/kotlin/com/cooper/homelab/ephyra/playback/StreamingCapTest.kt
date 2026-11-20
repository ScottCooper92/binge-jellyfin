package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** The user's own cap on the bitrate, as the server is asked for a source: it lowers the profile's ceiling and never raises it. */
class StreamingCapTest {
    private val jellyfin = FakeJellyfin()
    private val client =
        JellyfinClient(
            OkHttpClient(),
            jellyfin.baseUrl.toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1"),
            Dispatchers.IO,
            token = "token-1",
            logger = DiscardLogger,
        )
    private val item = PlayItem("movie-1", PlayTitle("The Long Voyage"))
    private val device =
        PlaybackDeviceProfile(videoCodecs = listOf("video/avc"), audioCodecs = listOf("audio/mp4a-latm"), maxBitrateBps = 40_000_000L)

    @After
    fun close() = jellyfin.close()

    private fun minter(cap: StreamingCap) = JellyfinSourceMinter(client, "user-1", JellyfinServerProfile.from("10.11.6"), cap)

    private fun asked(): Pair<Long?, Long?> {
        val body = jellyfin.lastBody("/Items/movie-1/PlaybackInfo")
        val profile = body["DeviceProfile"]!!.jsonObject
        return body["MaxStreamingBitrate"]?.jsonPrimitive?.content?.toLong() to
            profile["MaxStreamingBitrate"]?.jsonPrimitive?.content?.toLong()
    }

    @Test
    fun `no cap leaves what the device says, in the body and in the profile`() =
        runBlocking {
            minter(NoStreamingCap).mint(item, device, null)
            assertEquals(40_000_000L to 40_000_000L, asked())
        }

    @Test
    fun `a cap below the device's lowers the body and the profile to the same number`() =
        runBlocking {
            minter { 4_000_000L }.mint(item, device, null)
            assertEquals(4_000_000L to 4_000_000L, asked())
        }

    @Test
    fun `a cap above the device's does not raise it`() =
        runBlocking {
            minter { 120_000_000L }.mint(item, device, null)
            assertEquals(40_000_000L to 40_000_000L, asked())
        }

    @Test
    fun `a device that gave no ceiling is held to the cap`() =
        runBlocking {
            minter { 8_000_000L }.mint(item, device.copy(maxBitrateBps = 0L), null)
            assertEquals(8_000_000L to 8_000_000L, asked())
        }

    @Test
    fun `the cap is read at each mint, so a change applies from the next one`() =
        runBlocking {
            var cap: Long? = 4_000_000L
            val minter = minter { cap }
            minter.mint(item, device, null)
            assertEquals(4_000_000L, asked().first)
            cap = 10_000_000L
            minter.mint(item, device, null)
            assertEquals(10_000_000L, asked().first)
            cap = null
            minter.mint(item, device, null)
            assertEquals(40_000_000L, asked().first)
        }
}
