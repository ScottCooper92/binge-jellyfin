package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.TrackChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The streams a play's reports say are playing: the sitting's choice mapped onto the media source the play was minted from. */
class PlayingStreamsTest {
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
    private val minter = JellyfinSourceMinter(client, "user-1", JellyfinServerProfile.from("10.11.6"))
    private val reporter = JellyfinPlayReporter(client)
    private val item = PlayItem("movie-1", PlayTitle("Many Tracks"))
    private val profile = PlaybackDeviceProfile(videoCodecs = listOf("video/avc"), audioCodecs = listOf("audio/mp4a-latm"))

    @After
    fun close() = jellyfin.close()

    private fun source(
        defaultSubtitle: Int? = null,
        subtitleDelivery: String = "External",
    ) {
        val subtitleDefault = defaultSubtitle?.let { ""","DefaultSubtitleStreamIndex":$it""" }.orEmpty()
        jellyfin.playbackInfo =
            """{"PlaySessionId":"play-1","MediaSources":[{"Id":"source-1","Container":"mkv","SupportsDirectPlay":true,""" +
            """"SupportsDirectStream":true,"RunTimeTicks":36000000000,"DefaultAudioStreamIndex":1$subtitleDefault,"MediaStreams":[""" +
            """{"Type":"Audio","Index":1,"Language":"eng","Title":"Stereo","IsDefault":true},""" +
            """{"Type":"Audio","Index":2,"Language":"spa","Title":"Doblaje"},""" +
            """{"Type":"Subtitle","Index":3,"Language":"eng","Title":"English","DisplayTitle":"English",""" +
            """"DeliveryMethod":"$subtitleDelivery","DeliveryUrl":"/s/3.srt","Codec":"subrip"},""" +
            """{"Type":"Subtitle","Index":4,"Language":"spa","Title":"Spanish","DisplayTitle":"Spanish",""" +
            """"DeliveryMethod":"$subtitleDelivery","DeliveryUrl":"/s/4.srt","Codec":"subrip"}]}]}"""
    }

    private fun reported(
        choice: StreamChoice?,
        kind: PlayReport = PlayReport.PROGRESS,
    ): JsonObject =
        runBlocking {
            val play = minter.mint(item, profile, null, choice).report
            reporter.report(play, kind, 25_000L, choice)
            jellyfin.lastBody(if (kind == PlayReport.STARTED) "/Sessions/Playing" else "/Sessions/Playing/Progress")
        }

    private fun JsonObject.streams() = this["AudioStreamIndex"] to this["SubtitleStreamIndex"]

    @Test
    fun `before the player has said, the report names the server's own default streams, and no subtitle is minus one`() {
        source(defaultSubtitle = 3)
        assertEquals(JsonPrimitive(1) to JsonPrimitive(3), reported(null, PlayReport.STARTED).streams())
        source()
        assertEquals(JsonPrimitive(1) to JsonPrimitive(-1), reported(null).streams())
    }

    @Test
    fun `a choice is mapped to the streams it names, and subtitles off are minus one`() {
        source(defaultSubtitle = 3)
        assertEquals(
            JsonPrimitive(2) to JsonPrimitive(4),
            reported(StreamChoice(TrackChoice("es", null), TrackChoice("spa", null))).streams(),
        )
        assertEquals(JsonPrimitive(1) to JsonPrimitive(-1), reported(StreamChoice(TrackChoice("en", null), null)).streams())
    }

    @Test
    fun `a stream the user picked from the host's list is the audio, whatever the player calls it`() {
        source()
        val choice = StreamChoice(TrackChoice("eng", null), null, audioStream = 2)
        assertEquals(JsonPrimitive(2) to JsonPrimitive(-1), reported(choice).streams())
    }

    @Test
    fun `a choice naming nothing this source has leaves the server's defaults`() {
        source(defaultSubtitle = 3)
        val choice = StreamChoice(TrackChoice("fr", null), TrackChoice("fr", null))
        assertEquals(JsonPrimitive(1) to JsonPrimitive(3), reported(choice).streams())
    }

    @Test
    fun `a subtitle the server burns in is what plays, even when the player says subtitles are off`() {
        source(defaultSubtitle = 3, subtitleDelivery = "Encode")
        assertEquals(JsonPrimitive(1) to JsonPrimitive(3), reported(StreamChoice(TrackChoice("en", null), null)).streams())
    }

    @Test
    fun `started, progress, pause and resume say which streams play, and stopped and finished do not`() =
        runBlocking {
            source(defaultSubtitle = 3)
            val play = minter.mint(item, profile, null, null).report
            listOf(PlayReport.STARTED, PlayReport.PROGRESS, PlayReport.PAUSED, PlayReport.RESUMED).forEach {
                reporter.report(play, it, 1_000L, null)
            }
            val bodies = jellyfin.requests.filter { it.url.encodedPath.startsWith("/Sessions/Playing") }
            assertEquals(4, bodies.size)
            assertEquals(
                4,
                bodies.count {
                    it.body
                        ?.utf8()
                        .orEmpty()
                        .contains("\"AudioStreamIndex\":1,\"SubtitleStreamIndex\":3")
                },
            )
            reporter.report(play, PlayReport.STOPPED, 2_000L, null)
            assertFalse(jellyfin.lastBodyText("/Sessions/Playing/Stopped").contains("StreamIndex"))
        }

    @Test
    fun `a handle made with no media source names no streams`() =
        runBlocking {
            reporter.report(JellyfinPlayHandle("movie-1", "source-1", "play-1", "DirectPlay", 0L), PlayReport.STARTED, 0L, null)
            assertFalse(jellyfin.lastBodyText("/Sessions/Playing").contains("StreamIndex"))
        }
}
