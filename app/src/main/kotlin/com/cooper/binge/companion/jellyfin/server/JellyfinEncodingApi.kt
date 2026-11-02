package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject

// The server's transcoding settings: `EncodingOptions`, a named configuration (`/System/Configuration/encoding`),
// and the resume and streaming settings in `/System/Configuration`. Key names are the server's own (read at the
// release tags, and the choices as jellyfin-web 10.10 offers them); `docs/server-compatibility.md` says which
// release added the gated ones. The page changes one key at a time and sends the whole object back.

private const val ENCODING = "System/Configuration/encoding"

/** The server's whole encoding configuration, kept whole so a save sends back what it didn't change. */
suspend fun JellyfinClient.encodingConfiguration(): JsonObject = getObject(ENCODING)

suspend fun JellyfinClient.saveEncodingConfiguration(configuration: JsonObject) {
    post(ENCODING, configuration)
}

const val ENCODING_ACCELERATION = "HardwareAccelerationType"
const val ENCODING_VAAPI_DEVICE = "VaapiDevice"
const val ENCODING_QSV_DEVICE = "QsvDevice"
const val ENCODING_DECODING_CODECS = "HardwareDecodingCodecs"
const val ENCODING_HARDWARE_ENCODING = "EnableHardwareEncoding"
const val ENCODING_HEVC = "AllowHevcEncoding"
const val ENCODING_INTEL_LOW_POWER_HEVC = "EnableIntelLowPowerHevcHwEncoder"
const val ENCODING_ENHANCED_NVDEC = "EnableEnhancedNvdecDecoder"
const val ENCODING_SYSTEM_NATIVE = "PreferSystemNativeHwDecoder"
const val ENCODING_TONEMAPPING = "EnableTonemapping"
const val ENCODING_TONEMAPPING_ALGORITHM = "TonemappingAlgorithm"
const val ENCODING_TONEMAPPING_MODE = "TonemappingMode"
const val ENCODING_TONEMAPPING_RANGE = "TonemappingRange"
const val ENCODING_TONEMAPPING_DESAT = "TonemappingDesat"
const val ENCODING_TONEMAPPING_PEAK = "TonemappingPeak"
const val ENCODING_TONEMAPPING_PARAM = "TonemappingParam"
const val ENCODING_VPP_TONEMAPPING = "EnableVppTonemapping"
const val ENCODING_VPP_BRIGHTNESS = "VppTonemappingBrightness"
const val ENCODING_VPP_CONTRAST = "VppTonemappingContrast"
const val ENCODING_VIDEO_TOOLBOX_TONEMAPPING = "EnableVideoToolboxTonemapping"
const val ENCODING_PRESET = "EncoderPreset"
const val ENCODING_THREADS = "EncodingThreadCount"
const val ENCODING_DEINTERLACE = "DeinterlaceMethod"
const val ENCODING_DEINTERLACE_DOUBLE_RATE = "DeinterlaceDoubleRate"
const val ENCODING_DOWNMIX_BOOST = "DownMixAudioBoost"
const val ENCODING_DOWNMIX_ALGORITHM = "DownMixStereoAlgorithm"
const val ENCODING_AUDIO_VBR = "EnableAudioVbr"
const val ENCODING_THROTTLING = "EnableThrottling"
const val ENCODING_THROTTLE_DELAY = "ThrottleDelaySeconds"
const val ENCODING_SEGMENT_DELETION = "EnableSegmentDeletion"
const val ENCODING_SEGMENT_KEEP = "SegmentKeepSeconds"
const val ENCODING_SUBTITLE_EXTRACTION = "EnableSubtitleExtraction"
const val ENCODING_FALLBACK_FONT = "EnableFallbackFont"
const val ENCODING_FALLBACK_FONT_PATH = "FallbackFontPath"
const val ENCODING_TEMP_PATH = "TranscodingTempPath"

const val SERVER_MIN_RESUME_PERCENT = "MinResumePct"
const val SERVER_MAX_RESUME_PERCENT = "MaxResumePct"
const val SERVER_MIN_RESUME_SECONDS = "MinResumeDurationSeconds"
const val SERVER_MIN_AUDIOBOOK_RESUME = "MinAudiobookResume"
const val SERVER_MAX_AUDIOBOOK_RESUME = "MaxAudiobookResume"
const val SERVER_BITRATE_LIMIT = "RemoteClientBitrateLimit"

/** A stored choice as shown: blank (what 10.8 and 10.9 store for "none" and "auto") reads as [default]. */
fun encodingChoice(
    stored: String?,
    default: String,
): String = stored?.takeIf { it.isNotBlank() } ?: default

/** A chosen value as the server stores it: before 10.10 its default, "none" or "auto", is `""`. */
fun encodingStored(
    choice: String,
    default: String,
    profile: JellyfinServerProfile,
): String = if (choice == default && !profile.hasNamedEncodingDefaults) "" else choice

/** The hardware acceleration types any supported server has, as jellyfin-web lists them: `none` first. */
val ACCELERATION_TYPES = listOf("none", "amf", "nvenc", "qsv", "vaapi", "rkmpp", "videotoolbox", "v4l2m2m")

/** The types this server has: RKMPP arrived in 10.9. */
fun accelerationTypes(profile: JellyfinServerProfile): List<String> =
    if (profile.hasAdvancedEncoding) ACCELERATION_TYPES else ACCELERATION_TYPES - "rkmpp"

val TONEMAPPING_ALGORITHMS = listOf("none", "clip", "linear", "gamma", "reinhard", "hable", "mobius", "bt2390")
val TONEMAPPING_MODES = listOf("auto", "max", "rgb", "lum", "itp")
val TONEMAPPING_RANGES = listOf("auto", "tv", "pc")
val ENCODER_PRESETS = listOf("auto", "veryslow", "slower", "slow", "medium", "fast", "faster", "veryfast", "superfast", "ultrafast")
val DEINTERLACE_METHODS = listOf("yadif", "bwdif")

/** The stereo downmix algorithms; RFC 7845 and AC-4 are the server's from 10.10. */
fun downmixAlgorithms(profile: JellyfinServerProfile): List<String> =
    listOf("None", "Dave750", "NightmodeDialogue") + if (profile.hasQsvDeviceAndDownmix) listOf("Rfc7845", "Ac4") else emptyList()

/** The types that have tone mapping in hardware: every one but `none` and V4L2. */
private val TONEMAPPING_TYPES = setOf("amf", "nvenc", "qsv", "vaapi", "rkmpp", "videotoolbox")

/** The codecs a hardware decoder of [type] can be asked to take, as jellyfin-web offers them. */
fun decodingCodecs(type: String): List<String> =
    when (type) {
        "amf" -> listOf("h264", "hevc", "mpeg2video", "vc1", "vp9", "av1")
        "nvenc" -> listOf("h264", "hevc", "mpeg2video", "mpeg4", "vc1", "vp8", "vp9", "av1")
        "qsv", "vaapi" -> listOf("h264", "hevc", "mpeg2video", "vc1", "vp8", "vp9", "av1")
        "rkmpp" -> listOf("h264", "hevc", "mpeg1video", "mpeg2video", "mpeg4", "vp8", "vp9", "av1")
        "videotoolbox" -> listOf("h264", "hevc", "vp8", "vp9")
        "v4l2m2m" -> listOf("h264")
        else -> emptyList()
    }

/** Whether [type] does the work in hardware: the options for it show for anything but `none`. */
fun isAccelerated(type: String): Boolean = type != "none" && type.isNotBlank()

/** Whether the tone mapping options show for [type]: every hardware type that has them, and software from 10.10. */
fun hasToneMapping(
    type: String,
    profile: JellyfinServerProfile,
): Boolean = type in TONEMAPPING_TYPES || (type == "none" && profile.hasNamedEncodingDefaults)

/** The tone mapping mode is for hardware only. */
fun hasToneMappingMode(type: String): Boolean = type in TONEMAPPING_TYPES

/** The extra Intel switch (low power HEVC encoding) is for Quick Sync and VAAPI. */
fun hasIntelOptions(type: String): Boolean = type == "qsv" || type == "vaapi"
