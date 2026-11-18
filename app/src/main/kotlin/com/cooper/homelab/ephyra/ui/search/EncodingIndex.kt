package com.cooper.homelab.ephyra.ui.search

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.server.ENCODING_AUDIO_VBR
import com.cooper.homelab.ephyra.server.ENCODING_DECODE_HEVC_10
import com.cooper.homelab.ephyra.server.ENCODING_DECODE_HEVC_REXT_10
import com.cooper.homelab.ephyra.server.ENCODING_DECODE_HEVC_REXT_12
import com.cooper.homelab.ephyra.server.ENCODING_DECODE_VP9_10
import com.cooper.homelab.ephyra.server.ENCODING_DEINTERLACE_DOUBLE_RATE
import com.cooper.homelab.ephyra.server.ENCODING_ENHANCED_NVDEC
import com.cooper.homelab.ephyra.server.ENCODING_FALLBACK_FONT
import com.cooper.homelab.ephyra.server.ENCODING_HARDWARE_ENCODING
import com.cooper.homelab.ephyra.server.ENCODING_INTEL_LOW_POWER_H264
import com.cooper.homelab.ephyra.server.ENCODING_INTEL_LOW_POWER_HEVC
import com.cooper.homelab.ephyra.server.ENCODING_SEGMENT_DELETION
import com.cooper.homelab.ephyra.server.ENCODING_SUBTITLE_EXTRACTION
import com.cooper.homelab.ephyra.server.ENCODING_SYSTEM_NATIVE
import com.cooper.homelab.ephyra.server.ENCODING_THROTTLING
import com.cooper.homelab.ephyra.server.ENCODING_TONEMAPPING
import com.cooper.homelab.ephyra.server.ENCODING_VIDEO_TOOLBOX_TONEMAPPING
import com.cooper.homelab.ephyra.server.ENCODING_VPP_TONEMAPPING
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.ui.console.ConsolePage

private fun Resources.transcodingSwitch(
    @StringRes label: Int,
    icon: ImageVector,
    key: String,
    shown: (JellyfinServerProfile, JellyfinAccount) -> Boolean = { _, _ -> true },
) = setting(
    ConsolePage.Transcoding,
    label,
    icon,
    R.string.settings_server,
    R.string.transcoding_title,
    shown = shown,
    row = SettingRows.transcoding(key),
)

/**
 * The Transcoding page's other switches, for an administrator. The page draws many of them for some acceleration types only, which an
 * entry cannot see: the route reads the configuration when the search opens and hides the entries the page would not draw
 * ([drawnFor]). Where it cannot be read, they are all offered.
 */
internal fun Resources.indexEncodingSwitches(): List<SettingEntry<SearchTarget>> =
    listOf(
        transcodingSwitch(R.string.transcoding_decode_hevc_10, Icons.Outlined.Videocam, ENCODING_DECODE_HEVC_10),
        transcodingSwitch(R.string.transcoding_decode_vp9_10, Icons.Outlined.Videocam, ENCODING_DECODE_VP9_10),
        transcodingSwitch(R.string.transcoding_decode_rext_10, Icons.Outlined.Videocam, ENCODING_DECODE_HEVC_REXT_10) { profile, _ ->
            profile.hasHevcRext
        },
        transcodingSwitch(R.string.transcoding_decode_rext_12, Icons.Outlined.Videocam, ENCODING_DECODE_HEVC_REXT_12) { profile, _ ->
            profile.hasHevcRext
        },
        transcodingSwitch(R.string.transcoding_hardware_encoding, Icons.Outlined.Memory, ENCODING_HARDWARE_ENCODING),
        transcodingSwitch(R.string.transcoding_low_power, Icons.Outlined.Speed, ENCODING_INTEL_LOW_POWER_HEVC),
        transcodingSwitch(R.string.transcoding_low_power_h264, Icons.Outlined.Speed, ENCODING_INTEL_LOW_POWER_H264),
        transcodingSwitch(R.string.transcoding_nvdec, Icons.Outlined.Architecture, ENCODING_ENHANCED_NVDEC),
        transcodingSwitch(R.string.transcoding_native, Icons.Outlined.Memory, ENCODING_SYSTEM_NATIVE),
        transcodingSwitch(R.string.transcoding_tonemapping_enable, Icons.Outlined.Palette, ENCODING_TONEMAPPING),
        transcodingSwitch(R.string.transcoding_vpp, Icons.Outlined.Palette, ENCODING_VPP_TONEMAPPING),
        transcodingSwitch(R.string.transcoding_toolbox, Icons.Outlined.Palette, ENCODING_VIDEO_TOOLBOX_TONEMAPPING) { profile, _ ->
            profile.hasAdvancedEncoding
        },
        transcodingSwitch(R.string.transcoding_double_rate, Icons.Outlined.Speed, ENCODING_DEINTERLACE_DOUBLE_RATE),
        transcodingSwitch(R.string.transcoding_vbr, Icons.Outlined.AudioFile, ENCODING_AUDIO_VBR) { profile, _ ->
            profile.hasAdvancedEncoding
        },
        transcodingSwitch(R.string.transcoding_throttle, Icons.Outlined.Pause, ENCODING_THROTTLING),
        transcodingSwitch(R.string.transcoding_segment_deletion, Icons.Outlined.Layers, ENCODING_SEGMENT_DELETION) { profile, _ ->
            profile.hasAdvancedEncoding
        },
        transcodingSwitch(R.string.transcoding_extraction, Icons.Outlined.Subtitles, ENCODING_SUBTITLE_EXTRACTION),
        transcodingSwitch(R.string.transcoding_fallback_font, Icons.Outlined.FontDownload, ENCODING_FALLBACK_FONT),
    )
