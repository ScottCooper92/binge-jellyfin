package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfilesTest {
    private val pixel =
        PlaybackDeviceProfile
            .newBuilder()
            .addAllVideoCodecs(listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/unknown"))
            .addAllAudioCodecs(listOf("audio/mp4a-latm", "audio/ac3", "audio/eac3", "audio/opus"))
            .addAllContainerMimeTypes(listOf("video/mp4", "video/x-matroska", "application/x-mpegurl"))
            .setMaxWidth(2404)
            .setMaxHeight(1080)
            .setSupportsHdr(true)
            .build()

    private fun JsonObject.first(key: String): JsonObject = getValue(key).jsonArray.first().jsonObject

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    @Test
    fun `direct play lists what the device decodes, in Jellyfin's names`() {
        val direct = DeviceProfiles.toJellyfin(pixel).first("DirectPlayProfiles")
        assertEquals("h264,hevc,vp9", direct.text("VideoCodec"))
        assertEquals("aac,ac3,eac3,opus", direct.text("AudioCodec"))
        assertEquals("mp4,m4v,mov,mkv,matroska", direct.text("Container"))
    }

    @Test
    fun `without a JOC decoder, E-AC-3 stays out of the transcode and Atmos is ruled out`() {
        val profile = DeviceProfiles.toJellyfin(pixel)
        assertEquals("aac,ac3", profile.first("TranscodingProfiles").text("AudioCodec"))
        val audio = (profile.getValue("CodecProfiles") as JsonArray).map { it.jsonObject }.single { it.text("Type") == "VideoAudio" }
        assertEquals("eac3", audio.text("Codec"))
    }

    @Test
    fun `with a JOC decoder, E-AC-3 may be copied`() {
        val profile = DeviceProfiles.toJellyfin(pixel.toBuilder().addAudioCodecs("audio/eac3-joc").build())
        assertEquals("aac,ac3,eac3", profile.first("TranscodingProfiles").text("AudioCodec"))
        assertTrue((profile.getValue("CodecProfiles") as JsonArray).none { it.jsonObject.text("Type") == "VideoAudio" })
    }

    @Test
    fun `H264 is held to 8-bit, and the screen and HDR limits are conditions`() {
        val codecs =
            (
                DeviceProfiles
                    .toJellyfin(
                        pixel.toBuilder().setSupportsHdr(false).build(),
                    ).getValue("CodecProfiles") as JsonArray
            ).map {
                it.jsonObject
            }
        val h264 = codecs.single { it["Codec"]?.jsonPrimitive?.content == "h264" }.toString()
        assertTrue(h264.contains("\"Property\":\"VideoBitDepth\",\"Value\":\"8\""))
        val video = codecs.single { it.text("Type") == "Video" && it["Codec"] == null }.toString()
        assertTrue(video.contains("\"Width\",\"Value\":\"2404\""))
        assertTrue(video.contains("\"VideoRangeType\",\"Value\":\"SDR\""))
    }

    @Test
    fun `a device that said nothing gets the floor every Android device plays`() {
        val profile = DeviceProfiles.toJellyfin(null)
        assertEquals("h264", profile.first("DirectPlayProfiles").text("VideoCodec"))
        assertEquals("mp4,m4v", profile.first("DirectPlayProfiles").text("Container"))
        assertEquals("120000000", profile.text("MaxStreamingBitrate"))
        assertFalse(profile.toString().contains("VideoRangeType"))
    }

    @Test
    fun `the bitrate cap is the device's when it gives one`() {
        assertEquals(8_000_000L, DeviceProfiles.maxBitrate(pixel.toBuilder().setMaxBitrateBps(8_000_000).build()))
    }

    @Test
    fun `containers and subtitle codecs map to the MIME types the host accepts`() {
        assertEquals("video/mp4", DeviceProfiles.mimeForContainer("mov,mp4,m4a"))
        assertEquals("video/x-matroska", DeviceProfiles.mimeForContainer("mkv"))
        assertEquals("video/webm", DeviceProfiles.mimeForContainer("webm"))
        assertEquals("video/mp2t", DeviceProfiles.mimeForContainer("ts"))
        assertEquals("", DeviceProfiles.mimeForContainer("avi"))
        assertEquals("", DeviceProfiles.mimeForContainer(null))
        assertEquals("application/x-subrip", DeviceProfiles.subtitleMime("subrip"))
        assertEquals("text/x-ssa", DeviceProfiles.subtitleMime("ass"))
        assertEquals("application/ttml+xml", DeviceProfiles.subtitleMime("ttml"))
        assertEquals("text/vtt", DeviceProfiles.subtitleMime("webvtt"))
        assertNull(DeviceProfiles.subtitleMime("pgssub"))
    }

    private fun ranges(device: PlaybackDeviceProfile): List<String> {
        val video =
            (DeviceProfiles.toJellyfin(device).getValue("CodecProfiles") as JsonArray)
                .map { it.jsonObject }
                .single { it.text("Type") == "Video" && it["Codec"] == null }
        return video
            .getValue("Conditions")
            .jsonArray
            .map { it.jsonObject }
            .single { it.text("Property") == "VideoRangeType" }
            .text("Value")
            .split('|')
    }

    @Test
    fun `an HDR device without a Dolby Vision decoder plays only ranges with a base layer`() {
        val hdrNoDv = ranges(pixel)
        assertTrue("DOVIWithHDR10Plus" in hdrNoDv)
        assertTrue("HDR10" in hdrNoDv)
        assertFalse("DOVI" in hdrNoDv)
        assertFalse("DOVIWithEL" in hdrNoDv)

        val withDv = ranges(pixel.toBuilder().addVideoCodecs("video/dolby-vision").build())
        assertTrue("DOVI" in withDv)
        assertTrue("DOVIWithEL" in withDv)
    }
}
