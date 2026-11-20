package com.cooper.homelab.ephyra.playback

import android.content.Context
import com.cooper.homelab.ephyra.R

private const val SURROUND_5_1_CHANNELS = 6
private const val SURROUND_7_1_CHANNELS = 8

/**
 * The second line of an audio row, as the player words its own rows: the format and then the channel layout ("AAC mono", "Dolby Digital
 * 5.1"), in the user's language, and just the one where the stream gives only one. Never the server's own title for the stream, which
 * carries its language and a "Default" the row does not want.
 */
class ResourceAudioWording(
    private val context: Context,
) : AudioWording {
    override fun describe(format: AudioFormat): String? {
        val codec = codecName(format)?.let(context::getString)
        val layout = format.channels?.let(::layout)
        return when {
            codec != null && layout != null -> context.getString(R.string.client_audio_description, codec, layout)
            else -> codec ?: layout
        }
    }

    private fun layout(channels: Int): String =
        when (channels) {
            1 -> context.getString(R.string.client_audio_channels_mono)
            2 -> context.getString(R.string.client_audio_channels_stereo)
            SURROUND_5_1_CHANNELS -> context.getString(R.string.client_audio_channels_5_1)
            SURROUND_7_1_CHANNELS -> context.getString(R.string.client_audio_channels_7_1)
            else -> context.getString(R.string.client_audio_channels_n, channels)
        }

    private fun codecName(format: AudioFormat): Int? {
        val atmos = format.profile?.contains("atmos", ignoreCase = true) == true
        return when (format.codec?.lowercase()) {
            "ac3" -> R.string.client_audio_codec_dolby_digital
            "eac3" -> if (atmos) R.string.client_audio_codec_dolby_atmos else R.string.client_audio_codec_dolby_digital_plus
            "truehd" -> if (atmos) R.string.client_audio_codec_dolby_atmos else R.string.client_audio_codec_dolby_truehd
            "dts" ->
                if (format.profile?.contains("dts-hd", ignoreCase = true) ==
                    true
                ) {
                    R.string.client_audio_codec_dts_hd
                } else {
                    R.string.client_audio_codec_dts
                }
            "aac" -> R.string.client_audio_codec_aac
            "mp3" -> R.string.client_audio_codec_mp3
            "opus" -> R.string.client_audio_codec_opus
            "flac" -> R.string.client_audio_codec_flac
            "vorbis" -> R.string.client_audio_codec_vorbis
            else -> null
        }
    }
}
