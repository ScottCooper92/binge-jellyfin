package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.string

@Composable
internal fun accelerationLabel(type: String): String =
    stringResource(
        when (type) {
            "none" -> R.string.transcoding_type_none
            "amf" -> R.string.transcoding_type_amf
            "nvenc" -> R.string.transcoding_type_nvenc
            "qsv" -> R.string.transcoding_type_qsv
            "vaapi" -> R.string.transcoding_type_vaapi
            "rkmpp" -> R.string.transcoding_type_rkmpp
            "videotoolbox" -> R.string.transcoding_type_videotoolbox
            else -> R.string.transcoding_type_v4l2
        },
    )

@Composable
internal fun algorithmLabel(value: String): String =
    if (value ==
        "none"
    ) {
        stringResource(R.string.transcoding_none)
    } else if (value ==
        "bt2390"
    ) {
        "BT.2390"
    } else {
        value.replaceFirstChar { it.uppercase() }
    }

/** A mode or range as the server names it: Auto, or the letters in capitals. */
@Composable
internal fun modeLabel(value: String): String = if (value == "auto") stringResource(R.string.transcoding_auto) else value.uppercase()

@Composable
internal fun presetLabel(value: String): String = if (value == "auto") stringResource(R.string.transcoding_auto) else value

@Composable
internal fun deinterlaceLabel(value: String): String = value.replaceFirstChar { it.uppercase() }

@Composable
internal fun threadsLabel(threads: Int): String = if (threads < 0) stringResource(R.string.transcoding_auto) else threads.toString()

@Composable
internal fun downmixLabel(value: String): String =
    when (value) {
        "None" -> stringResource(R.string.transcoding_none)
        "Rfc7845" -> "RFC 7845"
        "Ac4" -> "AC-4"
        else -> value
    }

internal fun codecLabel(codec: String): Int =
    when (codec) {
        "h264" -> R.string.transcoding_codec_h264
        "hevc" -> R.string.transcoding_codec_hevc
        "mpeg1video" -> R.string.transcoding_codec_mpeg1
        "mpeg2video" -> R.string.transcoding_codec_mpeg2
        "mpeg4" -> R.string.transcoding_codec_mpeg4
        "vc1" -> R.string.transcoding_codec_vc1
        "vp8" -> R.string.transcoding_codec_vp8
        "vp9" -> R.string.transcoding_codec_vp9
        else -> R.string.transcoding_codec_av1
    }
