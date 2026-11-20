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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a remembered audio and subtitle choice does to the mint: the request names the matching streams, and only when they differ from the server's. */
class StreamChoiceTest {
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
    private val item = PlayItem("movie-1", PlayTitle("Many Tracks"))
    private val profile = PlaybackDeviceProfile(videoCodecs = listOf("video/avc"), audioCodecs = listOf("audio/mp4a-latm"), maxWidth = 1920)

    @After
    fun close() = jellyfin.close()

    private fun streams(vararg stream: String) {
        jellyfin.playbackInfo =
            """{"PlaySessionId":"play-1","MediaSources":[{"Id":"source-1","Container":"mkv","SupportsDirectPlay":true,""" +
            """"SupportsDirectStream":true,"RunTimeTicks":36000000000,"DefaultAudioStreamIndex":1,"MediaStreams":[""" +
            stream.joinToString(",") + "]}]}"
    }

    private fun audio(
        index: Int,
        language: String,
        title: String = "",
        default: Boolean = false,
    ) = """{"Type":"Audio","Index":$index,"Language":"$language","Title":"$title","DisplayTitle":"$title","IsDefault":$default}"""

    private fun subtitle(
        index: Int,
        language: String,
    ) = """{"Type":"Subtitle","Index":$index,"Language":"$language","DisplayTitle":"$language","DeliveryMethod":"Embed","Codec":"subrip"}"""

    private fun playbackInfoAsks() = jellyfin.requests.filter { it.url.encodedPath == "/Items/movie-1/PlaybackInfo" }

    private fun mint(choice: StreamChoice?) = runBlocking { minter.mint(item, profile, null, choice) }

    @Test
    fun `no choice is one ask, and the server's own defaults decide`() {
        streams(audio(1, "eng", default = true), audio(2, "spa"))
        mint(null)
        assertEquals(1, playbackInfoAsks().size)
    }

    @Test
    fun `a choice the server is already on is one ask`() {
        streams(audio(1, "eng", default = true), audio(2, "spa"))
        mint(StreamChoice(TrackChoice("en", null), null))
        assertEquals(1, playbackInfoAsks().size)
    }

    @Test
    fun `another audio language is asked for by its stream index, on the same media source`() {
        streams(audio(1, "eng", default = true), audio(2, "spa"))
        mint(StreamChoice(TrackChoice("es", null), null))
        val asks = playbackInfoAsks()
        assertEquals(2, asks.size)
        val url = asks.last().url
        assertEquals("2", url.queryParameter("audioStreamIndex"))
        assertEquals("source-1", url.queryParameter("mediaSourceId"))
        assertNull("subtitles were off and the server chose none", url.queryParameter("subtitleStreamIndex"))
    }

    @Test
    fun `a subtitle language is asked for by its stream index, and off is minus one`() {
        streams(audio(1, "eng", default = true), subtitle(3, "spa"))
        mint(StreamChoice(TrackChoice("en", null), TrackChoice("spa", null)))
        assertEquals("3", playbackInfoAsks().last().url.queryParameter("subtitleStreamIndex"))

        jellyfin.requests.clear()
        jellyfin.playbackInfo =
            jellyfin.playbackInfo.replace("\"DefaultAudioStreamIndex\":1", "\"DefaultAudioStreamIndex\":1,\"DefaultSubtitleStreamIndex\":3")
        mint(StreamChoice(TrackChoice("en", null), null))
        assertEquals("-1", playbackInfoAsks().last().url.queryParameter("subtitleStreamIndex"))
    }

    @Test
    fun `a label breaks a tie between two tracks in one language`() {
        streams(audio(1, "eng", "Stereo", default = true), audio(2, "eng", "Director's commentary"), audio(3, "spa"))
        mint(StreamChoice(TrackChoice("en", "Director's commentary"), null))
        assertEquals("2", playbackInfoAsks().last().url.queryParameter("audioStreamIndex"))
    }

    @Test
    fun `a choice the source has no track for leaves the server's default, and is no error`() {
        streams(audio(1, "eng", default = true), audio(2, "spa"))
        val minted = mint(StreamChoice(TrackChoice("fr", null), null))
        assertEquals(1, playbackInfoAsks().size)
        assertEquals(
            "source-1",
            minted.request.source.url
                .substringAfter("mediaSourceId=")
                .substringBefore('&'),
        )
    }

    @Test
    fun `subtitles the server burns into the video are not turned off by a player that never had them`() {
        streams(
            audio(1, "eng", default = true),
            """{"Type":"Subtitle","Index":3,"Language":"eng","DeliveryMethod":"Encode","Codec":"pgssub"}""",
        )
        jellyfin.playbackInfo =
            jellyfin.playbackInfo.replace("\"DefaultAudioStreamIndex\":1", "\"DefaultAudioStreamIndex\":1,\"DefaultSubtitleStreamIndex\":3")
        mint(StreamChoice(TrackChoice("en", null), null))
        assertEquals(1, playbackInfoAsks().size)
    }

    @Test
    fun `a second answer that cannot be played leaves the first, and the mint still succeeds`() {
        streams(audio(1, "eng", default = true), audio(2, "spa"))
        jellyfin.streamPlaybackInfo = """{"ErrorCode":"NoCompatibleStream","MediaSources":[]}"""
        val minted = mint(StreamChoice(TrackChoice("es", null), null))
        assertEquals(2, playbackInfoAsks().size)
        assertEquals(
            "the first answer's source",
            "source-1",
            minted.request.source.url
                .substringAfter("mediaSourceId=")
                .substringBefore('&'),
        )

        jellyfin.streamPlaybackInfo = """{"PlaySessionId":"p","MediaSources":[]}"""
        mint(StreamChoice(TrackChoice("es", null), null))
    }

    @Test
    fun `a language is one code however the tag or the file spells it`() {
        assertEquals("eng", languageCode("en"))
        assertEquals("eng", languageCode("eng"))
        assertEquals("eng", languageCode("en-GB"))
        assertEquals("spa", languageCode("es-419"))
        assertEquals("deu", languageCode("de"))
        assertEquals("deu", languageCode("ger"))
        assertEquals("fra", languageCode("fre"))
        assertNull(languageCode("und"))
        assertNull(languageCode(""))
        assertNull(languageCode(null))
    }
}
