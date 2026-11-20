package com.cooper.homelab.ephyra.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pieces Binge's player and the app's own share when they ask for a source and report a play. */
class PlaybackSupportTest {
    private val client =
        JellyfinClient(
            OkHttpClient(),
            "https://jf.example/".toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1"),
            Dispatchers.IO,
            token = "token-1",
            logger = DiscardLogger,
        )

    private fun source(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `there are ten thousand ticks in a millisecond and a thousand milliseconds in the second`() {
        assertEquals(10_000L, TICKS_PER_MILLI)
        assertEquals(TICKS_PER_SECOND, 1_000 * TICKS_PER_MILLI)
    }

    @Test
    fun `a key in an address is taken out in either spelling, and put back only where it is asked for and absent`() {
        assertEquals("https://jf.example/a?x=1", "https://jf.example/a?x=1&ApiKey=t&api_key=u".withoutApiKey())
        assertEquals("https://jf.example/a", "https://jf.example/a?API_KEY=t".withoutApiKey())
        assertEquals("not an address", "not an address".withoutApiKey())
        assertEquals("https://jf.example/a?x=1&ApiKey=t", "https://jf.example/a?x=1".withApiKey("t"))
        assertEquals("https://jf.example/a?ApiKey=t", "https://jf.example/a".withApiKey("t"))
        assertEquals("https://jf.example/a?ApiKey=old", "https://jf.example/a?ApiKey=old".withApiKey("t"))
        assertEquals("https://jf.example/a", "https://jf.example/a".withApiKey(null))
    }

    @Test
    fun `the file's address carries the token only where it is given one`() {
        assertEquals(
            "https://jf.example/Videos/m1/stream?static=true&mediaSourceId=s1&playSessionId=p1",
            client.directStreamUrl("m1", "s1", "p1"),
        )
        assertEquals("https://jf.example/Videos/m1/stream?static=true&mediaSourceId=s1", client.directStreamUrl("m1", "s1", ""))
        assertTrue(client.directStreamUrl("m1", "s1", "p1", token = "t").endsWith("&ApiKey=t"))
    }

    @Test
    fun `the server's choice is direct play, then a direct stream, and a transcode only when it has an address for one`() {
        assertEquals(PLAY_METHOD_DIRECT_PLAY, playMethodOf(source("""{"SupportsDirectPlay":true,"TranscodingUrl":"/t"}""")))
        assertEquals(PLAY_METHOD_DIRECT_STREAM, playMethodOf(source("""{"SupportsDirectStream":true,"TranscodingUrl":"/t"}""")))
        assertEquals(PLAY_METHOD_DIRECT_STREAM, playMethodOf(source("""{}""")))
        assertEquals(PLAY_METHOD_TRANSCODE, playMethodOf(source("""{"TranscodingUrl":"/t"}""")))
    }

    @Test
    fun `only external subtitles in a format a player reads are offered, ten at most`() {
        val streams =
            (1..12).joinToString(",") {
                """{"Type":"Subtitle","DeliveryMethod":"External","DeliveryUrl":"/s$it.vtt","Codec":"webvtt","Language":"eng","Title":"T$it"}"""
            }
        val list =
            source(
                """{"MediaStreams":[$streams,
                |{"Type":"Subtitle","DeliveryMethod":"Embed","Codec":"pgssub"},
                |{"Type":"Subtitle","DeliveryMethod":"External","DeliveryUrl":"/x","Codec":"pgssub"},
                |{"Type":"Audio","DeliveryMethod":"External","DeliveryUrl":"/a","Codec":"aac"}]}
                """.trimMargin(),
            ).externalSubtitles()
        assertEquals(10, list.size)
        assertEquals("/s1.vtt", list.first().path)
        assertEquals("T1", list.first().label)
        assertFalse(list.first().forced)
    }

    @Test
    fun `a report names the play and where it is`() {
        val body = playReportBody("i", "m", "p", PLAY_METHOD_TRANSCODE, 15_000_000L, paused = true).toString()
        assertEquals(
            """{"ItemId":"i","MediaSourceId":"m","PlaySessionId":"p","PlayMethod":"Transcode","PositionTicks":15000000,"CanSeek":true,"IsPaused":true}""",
            body,
        )
    }
}
