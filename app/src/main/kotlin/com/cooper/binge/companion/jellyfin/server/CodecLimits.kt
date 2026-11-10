package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.contracts.library.v1.CodecLimit
import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `PlaybackDeviceProfile.codec_limits` as Jellyfin `CodecProfile`s: the largest frame per video codec and profile, where a device
 * decodes some further than others (HEVC Main 10 to 4K, AVC High to 1080p).
 *
 * Jellyfin checks every `CodecProfile` that applies to a source, so a global `Width` and `Height` would still stop a 4K HEVC file
 * whatever a looser HEVC profile said. This takes the global limit's place: each codec in `video_codecs` gets the frame size the
 * contract resolves for it, the most specific entry first. The contract's order for a source in codec C and profile P is the entry
 * for (C, P), then the entry for C alone, then `max_width` and `max_height`, then no limit, and each dimension resolves on its own.
 */
internal object CodecLimits {
    /** Entries past this are not read, whether or not they are all-zero. */
    private const val MAX_ENTRIES = 32

    /**
     * The Jellyfin `VideoProfile` names for each profile token the contract defines. Jellyfin compares the container's own profile
     * text, case-insensitively and as a whole ("Main 10" for HEVC, as ffprobe reports it), so both spellings are listed.
     */
    private val PROFILES =
        mapOf(
            "video/avc" to
                mapOf(
                    "baseline" to listOf("baseline", "constrained baseline"),
                    "main" to listOf("main"),
                    "high" to listOf("high"),
                    "high10" to listOf("high 10", "high10"),
                ),
            "video/hevc" to mapOf("main" to listOf("main"), "main10" to listOf("main 10", "main10")),
            "video/av01" to mapOf("main" to listOf("main"), "high" to listOf("high"), "professional" to listOf("professional")),
        )

    /**
     * The `CodecProfile`s for [jellyfinCodecs] (lowercase MIME type to Jellyfin's name), the codecs Jellyfin may use for a play: those
     * the device lists, and the one an HLS transcode falls back to, so each keeps at least the maximum. None when [device] sent no
     * `codec_limits`, and the global limit then stands.
     */
    fun profiles(
        device: PlaybackDeviceProfile,
        jellyfinCodecs: Map<String, String>,
    ): List<JsonObject> {
        if (device.codecLimitsCount == 0) return emptyList()
        val entries = entries(device)
        return jellyfinCodecs.flatMap { (mime, codec) -> forCodec(mime, codec, entries, device) }
    }

    /** The first [MAX_ENTRIES] entries, minus any that are all-zero, and for one (codec, profile) the first that remains. */
    private fun entries(device: PlaybackDeviceProfile): Map<Pair<String, String>, CodecLimit> =
        device.codecLimitsList
            .take(MAX_ENTRIES)
            .filter { it.maxWidth > 0 || it.maxHeight > 0 }
            .distinctBy { key(it.mimeType, it.profile) }
            .associateBy { key(it.mimeType, it.profile) }

    private fun key(
        mime: String,
        profile: String,
    ) = mime.lowercase() to profile.lowercase()

    private fun forCodec(
        mime: String,
        codec: String,
        entries: Map<Pair<String, String>, CodecLimit>,
        device: PlaybackDeviceProfile,
    ): List<JsonObject> {
        val known = PROFILES[mime].orEmpty()
        // A profile token this app does not know matches only the codec-only entry, so an entry for it is left out.
        val listed = known.keys.filter { entries.containsKey(key(mime, it)) }

        fun limit(profile: String): Pair<Int, Int> {
            val tiers = listOfNotNull(entries[key(mime, profile)], entries[key(mime, "")])
            val width = tiers.firstOrNull { it.maxWidth > 0 }?.maxWidth ?: device.maxWidth
            val height = tiers.firstOrNull { it.maxHeight > 0 }?.maxHeight ?: device.maxHeight
            return width to height
        }
        val byProfile =
            listed.mapNotNull { token ->
                val names = known.getValue(token)
                // Required: a source whose profile is unknown is not this profile's, and falls to the codec's own limit below.
                val apply = listOf(DeviceProfiles.condition("EqualsAny", "VideoProfile", names.joinToString("|"), required = true))
                codecProfile(codec, apply, limit(token))
            }
        val others = listed.flatMap { known.getValue(it) }.map { DeviceProfiles.condition("NotEquals", "VideoProfile", it) }
        return byProfile + listOfNotNull(codecProfile(codec, others, limit("")))
    }

    private fun codecProfile(
        codec: String,
        apply: List<JsonElement>,
        limit: Pair<Int, Int>,
    ): JsonObject? {
        val (width, height) = limit
        val conditions =
            listOfNotNull(
                width.takeIf { it > 0 }?.let { DeviceProfiles.condition("LessThanEqual", "Width", it.toString()) },
                height.takeIf { it > 0 }?.let { DeviceProfiles.condition("LessThanEqual", "Height", it.toString()) },
            )
        if (conditions.isEmpty()) return null
        return DeviceProfiles.obj(
            "Type" to JsonPrimitive("Video"),
            "Codec" to JsonPrimitive(codec),
            "ApplyConditions" to JsonArray(apply),
            "Conditions" to JsonArray(conditions),
        )
    }
}
