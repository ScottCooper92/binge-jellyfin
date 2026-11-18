package com.cooper.binge.companion.jellyfin.ui.search

import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ENCODING_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.ENCODING_AUDIO_VBR
import com.cooper.binge.companion.jellyfin.server.ENCODING_AV1
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_HEVC_10
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_HEVC_REXT_10
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_HEVC_REXT_12
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODE_VP9_10
import com.cooper.binge.companion.jellyfin.server.ENCODING_DEINTERLACE_DOUBLE_RATE
import com.cooper.binge.companion.jellyfin.server.ENCODING_ENHANCED_NVDEC
import com.cooper.binge.companion.jellyfin.server.ENCODING_FALLBACK_FONT
import com.cooper.binge.companion.jellyfin.server.ENCODING_HARDWARE_ENCODING
import com.cooper.binge.companion.jellyfin.server.ENCODING_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_INTEL_LOW_POWER_H264
import com.cooper.binge.companion.jellyfin.server.ENCODING_INTEL_LOW_POWER_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_SEGMENT_DELETION
import com.cooper.binge.companion.jellyfin.server.ENCODING_SUBTITLE_EXTRACTION
import com.cooper.binge.companion.jellyfin.server.ENCODING_SYSTEM_NATIVE
import com.cooper.binge.companion.jellyfin.server.ENCODING_THROTTLING
import com.cooper.binge.companion.jellyfin.server.ENCODING_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.ENCODING_VIDEO_TOOLBOX_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.ENCODING_VPP_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.encodingChoice
import com.cooper.binge.companion.jellyfin.server.encodingConfiguration
import com.cooper.binge.companion.jellyfin.server.hasColorDepthDecoding
import com.cooper.binge.companion.jellyfin.server.hasHevcRextDecoding
import com.cooper.binge.companion.jellyfin.server.hasIntelOptions
import com.cooper.binge.companion.jellyfin.server.hasToneMapping
import com.cooper.binge.companion.jellyfin.server.isAccelerated
import com.cooper.binge.companion.jellyfin.server.string
import com.cooper.binge.companion.jellyfin.ui.console.consoleCall
import com.cooper.homelab.core.presentation.search.SettingEntry
import kotlinx.serialization.json.JsonObject

/** The acceleration type as the page reads it, which decides which of the Transcoding switches the page shows. */
private fun JsonObject.accelerationType(): String = encodingChoice(string(ENCODING_ACCELERATION), "none")

/**
 * A Transcoding switch: [gate] is the release that has it, and [shownFor] the acceleration types the page draws it for, as
 * the page's own rules say ([com.cooper.binge.companion.jellyfin.ui.console.TranscodingBody] and its groups use the same functions). It
 * reads null where the server did not send the key, as the page hides a row it was not sent.
 */
private fun encodingSpec(
    key: String,
    gate: (JellyfinServerProfile) -> Boolean = { true },
    shownFor: (type: String, profile: JellyfinServerProfile) -> Boolean = { _, _ -> true },
): Pair<String, SwitchSpec> =
    SettingRows.transcoding(key) to
        SwitchSpec(
            ConfigKind.Encoding,
            gate = gate,
            applies = { configuration, profile -> shownFor(configuration.accelerationType(), profile) },
            read = { if (key in it) it.bool(key) ?: false else null },
            change = topLevel(key),
        )

/** Whether the type does the work in hardware, which the page's encoding and decoding groups need. */
private val accelerated: (String, JellyfinServerProfile) -> Boolean = { type, _ -> isAccelerated(type) }

private val advancedEncoding: (JellyfinServerProfile) -> Boolean = { it.hasAdvancedEncoding }

/**
 * Whether the Transcoding page draws this entry's row for [encoding], the page's configuration as read when the search opened. An
 * entry that is no Transcoding switch is left alone, and so is every entry when [encoding] is null (it could not be read): the
 * search then offers what it always did. The same rule as the switch's own, so a result and its switch cannot disagree.
 */
internal fun SettingEntry<SearchTarget>.drawnFor(
    encoding: JsonObject?,
    profile: JellyfinServerProfile,
): Boolean {
    val spec = ENCODING_SPECS[row] ?: return true
    return encoding == null || (spec.applies(encoding, profile) && spec.read(encoding) != null)
}

/**
 * The encoding configuration, for the search to know which Transcoding rows the page draws; null where it cannot be read, for which
 * nothing is hidden. What a page has just saved goes first, so the acceleration type read is the one it chose.
 */
internal suspend fun Connection.encodingForSearch(): JsonObject? {
    awaitSaves()
    return consoleCall("JellyfinSearchAvailability") { _, client -> client.encodingConfiguration() }.getOrNull()
}

internal val ENCODING_SPECS: Map<String, SwitchSpec> =
    mapOf(
        encodingSpec(ENCODING_DECODE_HEVC_10, shownFor = { type, _ -> hasColorDepthDecoding(type) }),
        encodingSpec(ENCODING_DECODE_VP9_10, shownFor = { type, _ -> hasColorDepthDecoding(type) }),
        encodingSpec(ENCODING_DECODE_HEVC_REXT_10, shownFor = { type, profile -> hasHevcRextDecoding(type, profile) }),
        encodingSpec(ENCODING_DECODE_HEVC_REXT_12, shownFor = { type, profile -> hasHevcRextDecoding(type, profile) }),
        encodingSpec(ENCODING_HARDWARE_ENCODING, shownFor = accelerated),
        encodingSpec(ENCODING_HEVC, shownFor = accelerated),
        encodingSpec(ENCODING_AV1, gate = advancedEncoding, shownFor = accelerated),
        encodingSpec(ENCODING_INTEL_LOW_POWER_HEVC, shownFor = { type, _ -> hasIntelOptions(type) }),
        encodingSpec(ENCODING_INTEL_LOW_POWER_H264, shownFor = { type, _ -> hasIntelOptions(type) }),
        encodingSpec(ENCODING_ENHANCED_NVDEC, shownFor = { type, _ -> type == "nvenc" }),
        encodingSpec(ENCODING_SYSTEM_NATIVE, shownFor = { type, _ -> type == "qsv" }),
        encodingSpec(ENCODING_TONEMAPPING, shownFor = { type, profile -> hasToneMapping(type, profile) }),
        encodingSpec(ENCODING_VPP_TONEMAPPING, shownFor = { type, profile -> hasToneMapping(type, profile) && hasIntelOptions(type) }),
        encodingSpec(
            ENCODING_VIDEO_TOOLBOX_TONEMAPPING,
            gate = advancedEncoding,
            shownFor = { type, profile -> hasToneMapping(type, profile) && type == "videotoolbox" },
        ),
        encodingSpec(ENCODING_DEINTERLACE_DOUBLE_RATE),
        encodingSpec(ENCODING_AUDIO_VBR, gate = advancedEncoding),
        encodingSpec(ENCODING_THROTTLING),
        encodingSpec(ENCODING_SEGMENT_DELETION, gate = advancedEncoding),
        encodingSpec(ENCODING_SUBTITLE_EXTRACTION),
        encodingSpec(ENCODING_FALLBACK_FONT),
    )
