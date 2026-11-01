package com.cooper.binge.companion.jellyfin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinEncodingApiTest {
    @Test
    fun `each acceleration type offers the codecs jellyfin-web offers it`() {
        assertEquals(listOf("h264", "hevc", "mpeg2video", "vc1", "vp8", "vp9", "av1"), decodingCodecs("qsv"))
        assertEquals(decodingCodecs("qsv"), decodingCodecs("vaapi"))
        assertEquals(listOf("h264", "hevc", "mpeg2video", "vc1", "vp9", "av1"), decodingCodecs("amf"))
        assertTrue("mpeg4" in decodingCodecs("nvenc"))
        assertTrue("mpeg1video" in decodingCodecs("rkmpp"))
        assertEquals(listOf("h264", "hevc", "vp8", "vp9"), decodingCodecs("videotoolbox"))
        assertEquals(listOf("h264"), decodingCodecs("v4l2m2m"))
        assertEquals(emptyList<String>(), decodingCodecs("none"))
    }

    @Test
    fun `which options a type shows`() {
        assertFalse(isAccelerated("none"))
        assertFalse(isAccelerated(""))
        assertTrue(isAccelerated("v4l2m2m"))
        // Software tone mapping has its options and no switch; hardware has both; V4L2 has neither.
        assertTrue(hasToneMapping("none"))
        assertFalse(hasToneMappingSwitch("none"))
        assertTrue(hasToneMapping("nvenc") && hasToneMappingSwitch("nvenc"))
        assertFalse(hasToneMapping("v4l2m2m"))
        assertTrue(hasIntelOptions("qsv") && hasIntelOptions("vaapi"))
        assertFalse(hasIntelOptions("nvenc"))
    }

    @Test
    fun `the downmix algorithms RFC 7845 and AC-4 come with 10_10`() {
        assertEquals(listOf("None", "Dave750", "NightmodeDialogue"), downmixAlgorithms(JellyfinServerProfile.from("10.9.0")))
        assertEquals(
            listOf("None", "Dave750", "NightmodeDialogue", "Rfc7845", "Ac4"),
            downmixAlgorithms(JellyfinServerProfile.from("10.10.0")),
        )
    }

    @Test
    fun `every acceleration type is listed once, none first`() {
        assertEquals("none", ACCELERATION_TYPES.first())
        assertEquals(ACCELERATION_TYPES.size, ACCELERATION_TYPES.toSet().size)
    }
}
