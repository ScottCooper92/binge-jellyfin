package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.contracts.library.v1.CodecLimit
import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `codec_limits` as Jellyfin's per-codec frame-size conditions, and what stays of the single maximum. */
class CodecLimitsTest {
    private fun limit(
        mime: String,
        profile: String,
        width: Int,
        height: Int,
    ): CodecLimit =
        CodecLimit
            .newBuilder()
            .setMimeType(mime)
            .setProfile(profile)
            .setMaxWidth(width)
            .setMaxHeight(height)
            .build()

    private fun device(vararg limits: CodecLimit): PlaybackDeviceProfile =
        PlaybackDeviceProfile
            .newBuilder()
            .addAllVideoCodecs(listOf("video/avc", "video/hevc", "video/av01"))
            .setMaxWidth(1920)
            .setMaxHeight(1080)
            .setSupportsHdr(true)
            .addAllCodecLimits(limits.toList())
            .build()

    private fun codecProfiles(device: PlaybackDeviceProfile): List<JsonObject> =
        (DeviceProfiles.toJellyfin(device).getValue("CodecProfiles") as JsonArray).map { it.jsonObject }

    private fun JsonObject.conditions(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }

    private fun JsonObject.size(): Pair<String?, String?> {
        fun of(property: String) = conditions("Conditions").singleOrNull { it.text("Property") == property }?.text("Value")
        return of("Width") to of("Height")
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    /** The size rules for [codec] (Jellyfin's name), each with the profile condition that selects it, or none. */
    private fun rules(
        device: PlaybackDeviceProfile,
        codec: String,
    ): List<Pair<String, Pair<String?, String?>>> =
        codecProfiles(device)
            .filter { it["Codec"]?.jsonPrimitive?.content == codec && it["ApplyConditions"] != null }
            .filter { it.size() != (null to null) }
            .map { profile ->
                val apply = profile.conditions("ApplyConditions").joinToString(" & ") { "${it.text("Condition")} ${it.text("Value")}" }
                apply to profile.size()
            }

    private val globalSize: (PlaybackDeviceProfile) -> Pair<String?, String?> = { device ->
        codecProfiles(device).single { it.text("Type") == "Video" && it["Codec"] == null }.size()
    }

    @Test
    fun `with no codec limits the single maximum stays the global limit, as before`() {
        assertEquals("1920" to "1080", globalSize(device()))
        assertTrue(rules(device(), "hevc").isEmpty())
    }

    @Test
    fun `4K HEVC Main 10 over a 1080p AVC fallback gives each codec its own size, and the global one drops its size`() {
        val phone = device(limit("video/hevc", "main10", 3840, 2160))
        assertEquals(null to null, globalSize(phone))
        val hevc = rules(phone, "hevc")
        assertEquals("EqualsAny main 10|main10" to ("3840" to "2160"), hevc.first())
        // Every other HEVC profile, and the unknown one, falls to the fallback, as AVC does.
        assertEquals("NotEquals main 10" to ("1920" to "1080"), hevc.last().let { it.first.substringBefore(" & ") to it.second })
        assertEquals(listOf("" to ("1920" to "1080")).map { it.second }, rules(phone, "h264").map { it.second })
    }

    @Test
    fun `the most specific entry wins, then the codec's own, then the maximum`() {
        val phone =
            device(
                limit("video/avc", "", 1280, 720),
                limit("video/avc", "high", 1920, 1080),
                limit("video/hevc", "main10", 3840, 2160),
            )
        val avc = rules(phone, "h264")
        assertEquals("EqualsAny high" to ("1920" to "1080"), avc.first())
        assertEquals("1280" to "720", avc.last().second)
        // HEVC has no codec-only entry, so its other profiles take the maximum.
        assertEquals("1920" to "1080", rules(phone, "hevc").last().second)
    }

    @Test
    fun `each dimension resolves on its own, a zero falling to the next tier`() {
        val phone = device(limit("video/hevc", "main10", 0, 2160), limit("video/hevc", "", 2560, 0))
        // Main 10: no width of its own, so the codec's 2560; its own height.
        assertEquals("2560" to "2160", rules(phone, "hevc").first().second)
        // Other profiles: the codec's width, and no height of its own, so the maximum's.
        assertEquals("2560" to "1080", rules(phone, "hevc").last().second)
    }

    @Test
    fun `an all-zero entry is ignored and cannot shadow a later one for the same codec and profile`() {
        val phone = device(limit("video/hevc", "main10", 0, 0), limit("video/hevc", "main10", 3840, 2160))
        assertEquals("3840" to "2160", rules(phone, "hevc").first().second)
    }

    @Test
    fun `of two entries for one codec and profile the first that is not all-zero wins`() {
        val phone = device(limit("video/hevc", "main10", 3840, 2160), limit("video/hevc", "main10", 1280, 720))
        assertEquals("3840" to "2160", rules(phone, "hevc").first().second)
    }

    @Test
    fun `a profile token this app does not know matches only the codec-only entry`() {
        val phone = device(limit("video/hevc", "future10", 7680, 4320), limit("video/hevc", "", 2560, 1440))
        // No rule is made for the unknown token; the codec's own entry covers every profile.
        assertEquals(listOf("2560" to "1440"), rules(phone, "hevc").map { it.second })
    }

    @Test
    fun `only the first 32 entries are read, an all-zero one still taking a slot`() {
        val zeros = List(32) { limit("video/avc", "high", 0, 0) }
        val phone = device(*zeros.toTypedArray(), limit("video/hevc", "main10", 3840, 2160))
        // The HEVC entry is the 33rd, so it is past the cap and HEVC keeps the maximum.
        assertEquals(listOf("1920" to "1080"), rules(phone, "hevc").map { it.second })
    }

    @Test
    fun `a codec that is not in video codecs gets nothing, whatever an entry says`() {
        val noAv1 =
            PlaybackDeviceProfile
                .newBuilder()
                .addAllVideoCodecs(listOf("video/avc"))
                .setMaxWidth(1920)
                .setMaxHeight(1080)
                .addCodecLimits(limit("video/av01", "main", 3840, 2160))
                .build()
        assertTrue(rules(noAv1, "av1").isEmpty())
        assertNotNull(rules(noAv1, "h264").firstOrNull())
    }

    @Test
    fun `AVC baseline names both of Jellyfin's baselines, and high 10 is its own profile`() {
        val phone = device(limit("video/avc", "baseline", 1280, 720), limit("video/avc", "high10", 1920, 1080))
        val avc = rules(phone, "h264").map { it.first }
        assertTrue(avc.any { it == "EqualsAny baseline|constrained baseline" })
        assertTrue(avc.any { it == "EqualsAny high 10|high10" })
        assertFalse(avc.any { it == "EqualsAny high" })
    }

    @Test
    fun `the profile a rule applies to is required, so a source with an unknown profile falls to the codec's own limit`() {
        val phone = device(limit("video/hevc", "main10", 3840, 2160))
        val main10 = codecProfiles(phone).first { it["Codec"]?.jsonPrimitive?.content == "hevc" && it["ApplyConditions"] != null }
        assertEquals("true", main10.conditions("ApplyConditions").single().text("IsRequired"))
        val others = codecProfiles(phone).last { it["Codec"]?.jsonPrimitive?.content == "hevc" && it["ApplyConditions"] != null }
        assertTrue(others.conditions("ApplyConditions").all { it.text("IsRequired") == "false" })
    }
}
