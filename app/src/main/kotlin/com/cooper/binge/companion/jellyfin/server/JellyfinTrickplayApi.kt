package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The server's trickplay (seek thumbnail) options, from `TrickplayOptions` inside its configuration
 * (`/System/Configuration`). Admin only; 10.9 and later. The defaults are the server's own.
 */
data class TrickplaySettings(
    val hwAcceleration: Boolean = false,
    val hwEncoding: Boolean = false,
    /** Extract only key frames: faster, with rougher thumbnails. The server has it from 10.10. */
    val keyFrameOnly: Boolean = false,
    /** `NonBlocking` or `Blocking`: whether a library scan waits for the images. */
    val scanBehavior: String = "NonBlocking",
    /** How much of the processor ffmpeg may take: one of [TRICKPLAY_PRIORITIES]. */
    val processPriority: String = "BelowNormal",
    /** Milliseconds between two thumbnails. */
    val intervalMillis: Int = DEFAULT_INTERVAL_MILLIS,
    /** The widths, in pixels, a set of thumbnails is made at. */
    val widths: List<Int> = listOf(DEFAULT_WIDTH),
    val tileWidth: Int = DEFAULT_TILE,
    val tileHeight: Int = DEFAULT_TILE,
    val qscale: Int = DEFAULT_QSCALE,
    val jpegQuality: Int = DEFAULT_JPEG_QUALITY,
    /** ffmpeg's threads; 0 leaves it to ffmpeg. */
    val threads: Int = 1,
)

private const val DEFAULT_INTERVAL_MILLIS = 10_000
private const val DEFAULT_WIDTH = 320
private const val DEFAULT_TILE = 10
private const val DEFAULT_QSCALE = 4
private const val DEFAULT_JPEG_QUALITY = 90

/** The keys of `TrickplayOptions`, as the server names them. */
internal const val TRICKPLAY_OPTIONS = "TrickplayOptions"
internal const val TRICKPLAY_HW_ACCELERATION = "EnableHwAcceleration"
internal const val TRICKPLAY_HW_ENCODING = "EnableHwEncoding"
internal const val TRICKPLAY_KEY_FRAME_ONLY = "EnableKeyFrameOnlyExtraction"
internal const val TRICKPLAY_SCAN_BEHAVIOR = "ScanBehavior"
internal const val TRICKPLAY_PROCESS_PRIORITY = "ProcessPriority"
internal const val TRICKPLAY_INTERVAL = "Interval"
internal const val TRICKPLAY_WIDTHS = "WidthResolutions"
internal const val TRICKPLAY_TILE_WIDTH = "TileWidth"
internal const val TRICKPLAY_TILE_HEIGHT = "TileHeight"
internal const val TRICKPLAY_QSCALE = "Qscale"
internal const val TRICKPLAY_JPEG_QUALITY = "JpegQuality"
internal const val TRICKPLAY_THREADS = "ProcessThreads"

/** The scan behaviours the server has, as it names them: the default first. */
val TRICKPLAY_SCAN_BEHAVIORS = listOf("NonBlocking", "Blocking")

/** The priorities Jellyfin's own settings page offers, highest first; the server also has `RealTime`, which it leaves out. */
val TRICKPLAY_PRIORITIES = listOf("High", "AboveNormal", "Normal", "BelowNormal", "Idle")

/** The options object of a configuration, empty if the server sent none. */
internal fun JsonObject.trickplayOptions(): JsonObject = obj(TRICKPLAY_OPTIONS) ?: JsonObject(emptyMap())

fun JsonObject.trickplaySettings(): TrickplaySettings {
    val options = trickplayOptions()
    val defaults = TrickplaySettings()
    return TrickplaySettings(
        hwAcceleration = options.bool(TRICKPLAY_HW_ACCELERATION) ?: defaults.hwAcceleration,
        hwEncoding = options.bool(TRICKPLAY_HW_ENCODING) ?: defaults.hwEncoding,
        keyFrameOnly = options.bool(TRICKPLAY_KEY_FRAME_ONLY) ?: defaults.keyFrameOnly,
        scanBehavior = options.string(TRICKPLAY_SCAN_BEHAVIOR) ?: defaults.scanBehavior,
        processPriority = options.string(TRICKPLAY_PROCESS_PRIORITY) ?: defaults.processPriority,
        intervalMillis = options.int(TRICKPLAY_INTERVAL) ?: defaults.intervalMillis,
        widths = (options[TRICKPLAY_WIDTHS] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: defaults.widths,
        tileWidth = options.int(TRICKPLAY_TILE_WIDTH) ?: defaults.tileWidth,
        tileHeight = options.int(TRICKPLAY_TILE_HEIGHT) ?: defaults.tileHeight,
        qscale = options.int(TRICKPLAY_QSCALE) ?: defaults.qscale,
        jpegQuality = options.int(TRICKPLAY_JPEG_QUALITY) ?: defaults.jpegQuality,
        threads = options.int(TRICKPLAY_THREADS) ?: defaults.threads,
    )
}

/**
 * [this] configuration's options with [key] set to [value], the rest as the server sent them, so a
 * save puts back every option (and any this app doesn't know) it did not change.
 */
internal fun JsonObject.withTrickplayOption(
    key: String,
    value: kotlinx.serialization.json.JsonElement,
): JsonObject = JsonObject(trickplayOptions() + (key to value))
