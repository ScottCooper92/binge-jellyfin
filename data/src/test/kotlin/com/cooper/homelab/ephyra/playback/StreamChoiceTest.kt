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

    private fun textSubtitle(
        index: Int,
        language: String,
        inFile: Boolean,
        codec: String = "subrip",
    ) = """{"Type":"Subtitle","Index":$index,"Language":"$language","DisplayTitle":"$language","DeliveryMethod":"External",""" +
        """"DeliveryUrl":"/Videos/x/Subtitles/$index/0/Stream.srt","Codec":"$codec","IsExternal":${!inFile}}"""

    @Test
    fun `a direct play does not load a second copy of a text subtitle the file carries, and keeps one that is a file of its own`() {
        streams(
            textSubtitle(3, "eng", inFile = true),
            textSubtitle(4, "spa", inFile = false),
            textSubtitle(5, "fre", inFile = true, codec = "ttml"),
        )
        assertEquals(listOf("spa", "fre"), mint(null).request.subtitles.map { it.language })
    }

    @Test
    fun `a transcode keeps every text subtitle, since the stream has none of them`() {
        jellyfin.playbackInfo =
            """{"PlaySessionId":"p","MediaSources":[{"Id":"source-1","Container":"mkv","SupportsDirectPlay":false,""" +
            """"SupportsDirectStream":false,""" +
            """"TranscodingUrl":"/videos/x/master.m3u8?MediaSourceId=source-1","MediaStreams":[""" +
            textSubtitle(3, "eng", inFile = true) + "," + textSubtitle(4, "spa", inFile = true) + "]}]}"
        assertEquals(listOf("eng", "spa"), mint(null).request.subtitles.map { it.language })
    }

    @Test
    fun `a source with two audio streams offers both, the one the server chose marked current, and one stream offers nothing`() {
        streams(audio(1, "eng", "Stereo", default = true), audio(2, "spa", "Doblaje"))
        val options = mint(null).request.audioOptions
        assertEquals(listOf("1", "2"), options.map { it.key })
        assertEquals(listOf("eng", "spa"), options.map { it.language })
        assertEquals(listOf(true, false), options.map { it.current })
        assertEquals("with no wording there is no second line", listOf(null, null), options.map { it.description })

        streams(audio(1, "eng", "Stereo", default = true))
        assertEquals(emptyList<Any>(), mint(null).request.audioOptions)
    }

    @Test
    fun `the second line is the wording's, from the stream's codec and profile and channels, and never the server's own title`() {
        jellyfin.playbackInfo =
            """{"PlaySessionId":"p","MediaSources":[{"Id":"source-1","Container":"mkv","SupportsDirectPlay":true,""" +
            """"SupportsDirectStream":true,"DefaultAudioStreamIndex":1,"MediaStreams":[""" +
            """{"Type":"Audio","Index":1,"Language":"eng","DisplayTitle":"English - AAC - Mono - Default","Codec":"aac","Channels":1},""" +
            """{"Type":"Audio","Index":2,"Language":"spa","DisplayTitle":"Spanish - EAC3","Codec":"eac3",""" +
            """"Profile":"Dolby Digital Plus + Dolby Atmos","Channels":6}]}]}"""
        val worded =
            JellyfinSourceMinter(
                client,
                "user-1",
                JellyfinServerProfile.from("10.11.6"),
                wording = { "${it.codec}|${it.profile}|${it.channels}" },
            )
        val options = runBlocking { worded.mint(item, profile, null, null) }.request.audioOptions
        assertEquals(listOf("aac|null|1", "eac3|Dolby Digital Plus + Dolby Atmos|6"), options.map { it.description })
    }

    @Test
    fun `a stream the user picked from the list is asked for by its index, whatever the language says`() {
        streams(audio(1, "eng", "Stereo", default = true), audio(2, "eng", "Commentary"))
        mint(StreamChoice(TrackChoice("en", null), null, audioStream = 2))
        assertEquals("2", playbackInfoAsks().last().url.queryParameter("audioStreamIndex"))
    }

    @Test
    fun `a label with no language that matches no track names none`() {
        streams(audio(1, "eng", "Stereo", default = true), audio(2, "spa", "Doblaje"))
        mint(StreamChoice(TrackChoice(null, "Commentary"), null))
        assertEquals(1, playbackInfoAsks().size)
        jellyfin.requests.clear()
        mint(StreamChoice(TrackChoice(null, "Doblaje"), null))
        assertEquals("2", playbackInfoAsks().last().url.queryParameter("audioStreamIndex"))
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
