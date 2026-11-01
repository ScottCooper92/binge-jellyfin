package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The host's [PlaybackDeviceProfile] (Android MIME types, from MediaCodecList) as a Jellyfin
 * `DeviceProfile`: what the player can play as it is, and an HLS transcode for everything else. The
 * server decides between them; this app never inspects the file itself.
 */
object DeviceProfiles {
    private val VIDEO_CODECS =
        mapOf(
            "video/avc" to "h264",
            "video/hevc" to "hevc",
            "video/x-vnd.on2.vp9" to "vp9",
            "video/x-vnd.on2.vp8" to "vp8",
            "video/av01" to "av1",
            "video/mpeg2" to "mpeg2video",
            "video/mp4v-es" to "mpeg4",
        )

    private val AUDIO_CODECS =
        mapOf(
            "audio/mp4a-latm" to "aac",
            "audio/ac3" to "ac3",
            "audio/eac3" to "eac3",
            "audio/opus" to "opus",
            "audio/vorbis" to "vorbis",
            "audio/flac" to "flac",
            "audio/mpeg" to "mp3",
            "audio/true-hd" to "truehd",
            "audio/vnd.dts" to "dts",
            "audio/vnd.dts.hd" to "dts",
        )

    private val CONTAINERS =
        mapOf(
            "video/mp4" to listOf("mp4", "m4v", "mov"),
            "video/webm" to listOf("webm"),
            "video/x-matroska" to listOf("mkv", "matroska"),
        )

    /** What a player that said nothing about itself plays: H.264 and AAC in MP4, the floor every Android device meets. */
    private val FLOOR_VIDEO = listOf("h264")
    private val FLOOR_AUDIO = listOf("aac", "mp3")
    private val FLOOR_CONTAINERS = listOf("mp4", "m4v")

    private const val DEFAULT_MAX_BITRATE = 120_000_000L
    private const val EAC3_JOC = "audio/eac3-joc"
    private const val DOLBY_VISION = "video/dolby-vision"
    private val BASE_LAYER_RANGES =
        listOf("SDR", "HDR10", "HLG", "HDR10Plus", "DOVIWithHDR10", "DOVIWithHLG", "DOVIWithSDR", "DOVIWithHDR10Plus")
    private val DOLBY_VISION_RANGES = BASE_LAYER_RANGES + "DOVI"

    fun maxBitrate(device: PlaybackDeviceProfile?): Long = device?.maxBitrateBps?.takeIf { it > 0 } ?: DEFAULT_MAX_BITRATE

    fun toJellyfin(device: PlaybackDeviceProfile?): JsonObject {
        val audio =
            device
                ?.audioCodecsList
                .orEmpty()
                .mapNotNull { AUDIO_CODECS[it.lowercase()] }
                .distinct()
                .ifEmpty { FLOOR_AUDIO }
        val decodesAtmos = device?.audioCodecsList.orEmpty().any { it.equals(EAC3_JOC, ignoreCase = true) }
        val video =
            device
                ?.videoCodecsList
                .orEmpty()
                .mapNotNull { VIDEO_CODECS[it.lowercase()] }
                .distinct()
                .ifEmpty { FLOOR_VIDEO }
        val containers =
            device
                ?.containerMimeTypesList
                .orEmpty()
                .flatMap { CONTAINERS[it.lowercase()].orEmpty() }
                .distinct()
                .ifEmpty { FLOOR_CONTAINERS }
        return obj(
            "Name" to str("Binge"),
            "MaxStreamingBitrate" to JsonPrimitive(maxBitrate(device)),
            "MaxStaticBitrate" to JsonPrimitive(maxBitrate(device)),
            "DirectPlayProfiles" to
                JsonArray(
                    listOf(
                        obj(
                            "Type" to str("Video"),
                            "Container" to str(containers.joinToString(",")),
                            "VideoCodec" to str(video.joinToString(",")),
                            "AudioCodec" to str(audio.joinToString(",")),
                        ),
                    ),
                ),
            "TranscodingProfiles" to JsonArray(listOf(transcoding(video, audio, decodesAtmos))),
            "CodecProfiles" to JsonArray(codecProfiles(device, decodesAtmos)),
            "SubtitleProfiles" to
                JsonArray(
                    listOf("vtt", "srt", "subrip", "ass", "ssa", "ttml").map { obj("Format" to str(it), "Method" to str("External")) },
                ),
        )
    }

    /**
     * The HLS transcode for whatever cannot play directly. A transcode copies an E-AC-3 track as it is,
     * Atmos included, and no codec profile stops that copy: without a JOC decoder, E-AC-3 stays out of
     * the transcode and the server re-encodes the audio.
     */
    private fun transcoding(
        video: List<String>,
        audio: List<String>,
        decodesAtmos: Boolean,
    ): JsonObject {
        val hlsVideo = video.filter { it == "h264" || it == "hevc" }.ifEmpty { FLOOR_VIDEO }
        val hlsAudio = audio.filter { it in setOf("aac", "ac3", "mp3") || (it == "eac3" && decodesAtmos) }.ifEmpty { listOf("aac") }
        return obj(
            "Type" to str("Video"),
            "Container" to str("ts"),
            "Protocol" to str("hls"),
            "Context" to str("Streaming"),
            "VideoCodec" to str(hlsVideo.joinToString(",")),
            "AudioCodec" to str(hlsAudio.joinToString(",")),
            "MinSegments" to JsonPrimitive(1),
            "BreakOnNonKeyFrames" to JsonPrimitive(true),
        )
    }

    private fun codecProfiles(
        device: PlaybackDeviceProfile?,
        decodesAtmos: Boolean,
    ): List<JsonObject> =
        buildList {
            val limits = mutableListOf<JsonElement>()
            device?.maxWidth?.takeIf { it > 0 }?.let { limits += condition("LessThanEqual", "Width", it.toString()) }
            device?.maxHeight?.takeIf { it > 0 }?.let { limits += condition("LessThanEqual", "Height", it.toString()) }
            device?.let { limits += condition("EqualsAny", "VideoRangeType", playableRanges(it).joinToString("|")) }
            if (limits.isNotEmpty()) add(obj("Type" to str("Video"), "Conditions" to JsonArray(limits)))
            // E-AC-3 with Atmos (JOC) needs its own decoder; a device with plain E-AC-3 only fails to start on it.
            if (!decodesAtmos) {
                add(
                    obj(
                        "Type" to str("VideoAudio"),
                        "Codec" to str("eac3"),
                        "Conditions" to JsonArray(listOf(condition("NotEquals", "AudioProfile", "Dolby Digital Plus + Dolby Atmos"))),
                    ),
                )
            }
            // Android decodes 8-bit H.264 only: without this a 10-bit source transcodes to High 10, which no phone plays.
            add(
                obj(
                    "Type" to str("Video"),
                    "Codec" to str("h264"),
                    "Conditions" to
                        JsonArray(
                            listOf(
                                condition("LessThanEqual", "VideoBitDepth", "8"),
                                condition("EqualsAny", "VideoProfile", "high|main|baseline|constrained baseline"),
                            ),
                        ),
                ),
            )
        }

    /**
     * The video ranges [device] can show. Without HDR, SDR alone. With HDR but no Dolby Vision decoder,
     * the ranges whose base layer a plain HEVC or AV1 decoder plays: Dolby Vision with an HDR10, HLG,
     * SDR or HDR10+ base (profiles 8 and 9; Media3 falls back to the base layer for them). Profile 5
     * (`DOVI`) has no such base, so only a Dolby Vision decoder plays it. The enhancement-layer ranges
     * (`DOVIWithEL…`, profile 7) are left to the server whatever the device has: they need a dual-layer
     * decode, and the Android decoders seen (the Google TV Streamer's, for one) handle profiles 5 and 8
     * only. Jellyfin then sends the HDR10 base layer, or transcodes. The host's profile doesn't say which
     * Dolby Vision profiles the decoder takes, so none is assumed to take profile 7.
     */
    private fun playableRanges(device: PlaybackDeviceProfile): List<String> =
        when {
            !device.supportsHdr -> listOf("SDR")
            device.videoCodecsList.any { it.equals(DOLBY_VISION, ignoreCase = true) } -> DOLBY_VISION_RANGES
            else -> BASE_LAYER_RANGES
        }

    /** A Jellyfin container name as the MIME type Binge's allowlist takes. */
    fun mimeForContainer(container: String?): String {
        val first =
            container
                ?.split(',')
                ?.firstOrNull()
                ?.trim()
                ?.lowercase()
        return when (first) {
            "mp4", "m4v", "mov" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv", "matroska" -> "video/x-matroska"
            "ts", "mpegts" -> "video/mp2t"
            else -> ""
        }
    }

    /** A subtitle codec as the MIME type the host's player parses; null for one it cannot. */
    fun subtitleMime(codec: String?): String? =
        when (codec?.lowercase()) {
            "vtt", "webvtt" -> "text/vtt"
            "srt", "subrip" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "ttml" -> "application/ttml+xml"
            else -> null
        }

    private fun condition(
        type: String,
        property: String,
        value: String,
    ): JsonObject =
        obj(
            "Condition" to str(type),
            "Property" to str(property),
            "Value" to str(value),
            "IsRequired" to JsonPrimitive(false),
        )

    private fun obj(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(pairs.toMap())

    private fun str(value: String): JsonPrimitive = JsonPrimitive(value)
}
