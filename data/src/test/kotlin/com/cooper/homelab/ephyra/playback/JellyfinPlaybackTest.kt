package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.FakeItem
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.player.api.CodecLimit
import com.cooper.homelab.player.api.PlayMethod
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The minter and the reporter against the fake server: what a play asks the server for, and what it tells it. */
class JellyfinPlaybackTest {
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
    private val minter = JellyfinSourceMinter(client, "user-1")
    private val reporter = JellyfinPlayReporter(client)
    private val item = PlayItem("movie-1", PlayTitle("The Long Voyage"))
    private val profile = PlaybackDeviceProfile(videoCodecs = listOf("video/avc"), audioCodecs = listOf("audio/mp4a-latm"), maxWidth = 1920)

    @After
    fun close() = jellyfin.close()

    @Test
    fun `a source the device plays as it is is the file itself with the token in the header only`() =
        runBlocking {
            val request = minter.mint(item, profile, null).request
            assertEquals(PlayMethod.DIRECT_PLAY, request.source.method)
            assertEquals("video/mp4", request.source.mimeType)
            val url = request.source.url.toHttpUrl()
            assertEquals("/Videos/movie-1/stream", url.encodedPath)
            assertEquals("source-1", url.queryParameter("mediaSourceId"))
            assertEquals("play-1", url.queryParameter("playSessionId"))
            assertEquals(listOf("Authorization"), request.source.headers.map { it.first })
            assertTrue(
                request.source.headers
                    .single()
                    .second
                    .contains("Token=\"token-1\""),
            )
            assertFalse(request.source.url, request.source.url.contains("token-1"))
            assertEquals(3_600_000L, request.runtimeMs)
        }

    @Test
    fun `a source the device cannot play is the server's HLS transcode, with the token taken out of its address`() =
        runBlocking {
            jellyfin.playbackInfo = FakeJellyfin.transcodeInfo()
            val request = minter.mint(item, profile, null).request
            assertEquals(PlayMethod.TRANSCODE, request.source.method)
            assertEquals("application/x-mpegURL", request.source.mimeType)
            assertTrue(request.source.url, request.source.url.contains("/videos/x/master.m3u8"))
            assertFalse(request.source.url, request.source.url.contains("token-1") || request.source.url.contains("ApiKey"))
            assertTrue(
                request.source.headers
                    .single()
                    .second
                    .contains("Token=\"token-1\""),
            )
        }

    @Test
    fun `an external subtitle is a track with no token in its address, and an embedded one stays in the stream`() =
        runBlocking {
            val tracks = minter.mint(item, profile, null).request.subtitles
            val track = tracks.single()
            assertEquals("eng", track.language)
            assertEquals("English", track.label)
            assertEquals("text/vtt", track.mimeType)
            assertTrue(track.hearingImpaired)
            assertFalse(track.forced)
            assertFalse(track.url, track.url.contains("token-1"))
            assertTrue(track.url, track.url.endsWith("/Videos/x/Subtitles/2/0/Stream.vtt"))
        }

    @Test
    fun `the device profile reaches the server as a Jellyfin profile`() =
        runBlocking {
            val limited = profile.copy(codecLimits = listOf(CodecLimit("video/avc", "high", 1920, 1080)), maxBitrateBps = 20_000_000L)
            minter.mint(item, limited, null)
            val body = jellyfin.lastBody("/Items/movie-1/PlaybackInfo")
            assertEquals("user-1", body["UserId"]?.jsonPrimitive?.content)
            assertEquals(20_000_000L, body["MaxStreamingBitrate"]?.jsonPrimitive?.content?.toLong())
            val direct =
                body["DeviceProfile"]!!
                    .jsonObject["DirectPlayProfiles"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            assertEquals("h264", direct["VideoCodec"]?.jsonPrimitive?.content)
            assertEquals("aac", direct["AudioCodec"]?.jsonPrimitive?.content)
            assertTrue(body["DeviceProfile"]!!.jsonObject["CodecProfiles"].toString().contains("1920"))
        }

    @Test
    fun `a play resumes where the server last had the user, and a played item starts over`() =
        runBlocking {
            jellyfin.items += FakeItem("movie-1", "The Long Voyage", "Movie", positionTicks = 12_000_000_000L)
            val request = minter.mint(item, profile, null).request
            assertEquals(1_200_000L, request.resumePositionMs)
            assertEquals(
                "12000000000",
                jellyfin.requests
                    .last {
                        it.url.encodedPath == "/Items/movie-1/PlaybackInfo"
                    }.url
                    .queryParameter("startTimeTicks"),
            )
            jellyfin.items.clear()
            jellyfin.items += FakeItem("movie-1", "The Long Voyage", "Movie", played = true, positionTicks = 12_000_000_000L)
            assertEquals(0L, minter.mint(item, profile, null).request.resumePositionMs)
        }

    @Test
    fun `a position the sitting gives wins over the server's, and the server is not asked for it`() =
        runBlocking {
            jellyfin.items += FakeItem("movie-1", "The Long Voyage", "Movie", positionTicks = 12_000_000_000L)
            val request = minter.mint(item, profile, 30_000L).request
            assertEquals(30_000L, request.resumePositionMs)
            assertEquals(0, jellyfin.requests.count { it.url.encodedPath == "/Items" })
        }

    @Test
    fun `a refusal the server gives is a status, and a server with no source for the item is not found`() {
        jellyfin.playbackInfo = """{"ErrorCode":"NotAllowed","MediaSources":[]}"""
        assertEquals(403, assertThrows(JellyfinHttpException::class.java) { runBlocking { minter.mint(item, profile, null) } }.code)
        jellyfin.playbackInfo = """{"ErrorCode":"NoCompatibleStream","MediaSources":[]}"""
        assertEquals(409, assertThrows(JellyfinHttpException::class.java) { runBlocking { minter.mint(item, profile, null) } }.code)
        jellyfin.playbackInfo = """{"PlaySessionId":"p","MediaSources":[]}"""
        assertEquals(404, assertThrows(JellyfinHttpException::class.java) { runBlocking { minter.mint(item, profile, null) } }.code)
    }

    private fun handle() = JellyfinPlayHandle("movie-1", "source-1", "play-1", "DirectPlay", runtimeMs = 3_600_000L)

    private fun body(path: String): JsonObject = jellyfin.lastBody(path)

    @Test
    fun `a play is reported as the server's sessions, with its position in ticks`() =
        runBlocking {
            val play = handle()
            reporter.report(play, PlayReport.STARTED, 15_000L)
            reporter.report(play, PlayReport.PROGRESS, 25_000L)
            reporter.report(play, PlayReport.PAUSED, 26_000L)
            assertEquals(JsonPrimitive(150_000_000L), body("/Sessions/Playing")["PositionTicks"])
            assertEquals(JsonPrimitive("play-1"), body("/Sessions/Playing")["PlaySessionId"])
            assertEquals(JsonPrimitive("source-1"), body("/Sessions/Playing")["MediaSourceId"])
            assertEquals(JsonPrimitive("DirectPlay"), body("/Sessions/Playing")["PlayMethod"])
            assertEquals(JsonPrimitive(true), body("/Sessions/Playing/Progress")["IsPaused"])
            assertEquals(JsonPrimitive(260_000_000L), body("/Sessions/Playing/Progress")["PositionTicks"])
            reporter.report(play, PlayReport.RESUMED, 27_000L)
            assertEquals(JsonPrimitive(false), body("/Sessions/Playing/Progress")["IsPaused"])
            reporter.report(play, PlayReport.STOPPED, 40_000L)
            assertEquals(JsonPrimitive(400_000_000L), body("/Sessions/Playing/Stopped")["PositionTicks"])
            assertEquals(3, jellyfin.hits("/Sessions/Playing/Progress"))
        }

    @Test
    fun `a finished play is a stop at the end, and nothing after it moves the position back`() =
        runBlocking {
            val play = handle()
            reporter.report(play, PlayReport.PROGRESS, 3_000_000L)
            reporter.report(play, PlayReport.FINISHED, 3_400_000L)
            assertEquals(JsonPrimitive(36_000_000_000L), body("/Sessions/Playing/Stopped")["PositionTicks"])
            reporter.report(play, PlayReport.PROGRESS, 3_500_000L)
            reporter.report(play, PlayReport.STOPPED, 3_550_000L)
            assertEquals(1, jellyfin.hits("/Sessions/Playing/Stopped"))
            assertEquals(1, jellyfin.hits("/Sessions/Playing/Progress"))
        }

    @Test
    fun `a finish the server did not get is not counted, so the stop that follows still reaches it`() {
        val play = handle()
        jellyfin.status["/Sessions/Playing/Stopped"] = 500
        assertThrows(JellyfinHttpException::class.java) { runBlocking { reporter.report(play, PlayReport.FINISHED, 3_400_000L) } }
        assertFalse(play.isFinished)
        jellyfin.status.clear()
        runBlocking { reporter.report(play, PlayReport.STOPPED, 3_400_000L) }
        assertEquals(JsonPrimitive(34_000_000_000L), body("/Sessions/Playing/Stopped")["PositionTicks"])
    }

    @Test
    fun `a play with no runtime finishes at the position it reached`() =
        runBlocking {
            val play = JellyfinPlayHandle("movie-1", "source-1", "play-1", "Transcode", runtimeMs = 0L)
            reporter.report(play, PlayReport.FINISHED, 90_000L)
            assertEquals(JsonPrimitive(900_000_000L), body("/Sessions/Playing/Stopped")["PositionTicks"])
            assertEquals(JsonPrimitive("Transcode"), body("/Sessions/Playing/Stopped")["PlayMethod"])
        }

    @Test
    fun `a handle this reporter did not mint is refused`() {
        val stranger = object : PlayReportHandle {}
        assertThrows(IllegalArgumentException::class.java) { runBlocking { reporter.report(stranger, PlayReport.STARTED, 0L) } }
        assertNull(jellyfin.requests.firstOrNull { it.url.encodedPath.startsWith("/Sessions/Playing") })
    }
}
