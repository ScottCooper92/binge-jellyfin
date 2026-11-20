package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.array
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.server.string
import com.cooper.homelab.player.api.AudioCodec
import com.cooper.homelab.player.api.PlayAudioOption
import com.cooper.homelab.player.api.TrackChoice
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.MissingResourceException

/** The stream index a request sends for subtitles that are off. */
internal const val SUBTITLES_OFF = -1

/** The streams of a media source a [StreamChoice] names, as the server numbers them; null for one it names no stream of. */
internal class StreamIndexes(
    val audio: Int?,
    val subtitle: Int?,
)

/**
 * Finds [choice] in the media [source]'s streams. A choice the source has no stream for gives null, which leaves that track to the
 * server's own default: a title with a different set of tracks is not an error.
 */
internal fun JsonObject.streamIndexes(choice: StreamChoice): StreamIndexes {
    val subtitle = choice.subtitle
    return StreamIndexes(
        audio = choice.audioStream ?: choice.audio?.let { indexOf("Audio", it) },
        subtitle = if (subtitle == null) SUBTITLES_OFF else indexOf("Subtitle", subtitle),
    )
}

/** What the server already chose for this source: its default audio, and its default subtitle, or [SUBTITLES_OFF] when it chose none. */
internal fun JsonObject.chosenIndexes(): StreamIndexes =
    StreamIndexes(
        audio = this["DefaultAudioStreamIndex"]?.jsonPrimitive?.intOrNull,
        subtitle = this["DefaultSubtitleStreamIndex"]?.jsonPrimitive?.intOrNull ?: SUBTITLES_OFF,
    )

/**
 * Whether the server burns its default subtitle into the video of a transcode (`Encode`). The player never has that track, so what it
 * says of subtitles says nothing of it: a "subtitles off" from it is not the user's.
 */
internal fun JsonObject.burnsInSubtitle(): Boolean {
    val index = this["DefaultSubtitleStreamIndex"]?.jsonPrimitive?.intOrNull ?: return false
    return array("MediaStreams").filterIsInstance<JsonObject>().any {
        it.string("Type") == "Subtitle" && it["Index"]?.jsonPrimitive?.intOrNull == index && it.string("DeliveryMethod") == "Encode"
    }
}

private fun JsonObject.indexOf(
    type: String,
    choice: TrackChoice,
): Int? {
    val streams = array("MediaStreams").filterIsInstance<JsonObject>().filter { it.string("Type") == type }
    val language = choice.language?.let(::languageCode)
    val sameLanguage = if (language == null) streams else streams.filter { languageCode(it.string("Language")) == language }
    if (sameLanguage.isEmpty() || (language == null && choice.label == null)) return null
    val labelled = choice.label?.let { label -> sameLanguage.firstOrNull { it.named(label) } }
    if (language == null && labelled == null) return null
    val picked = labelled ?: sameLanguage.firstOrNull { it.bool("IsDefault") == true } ?: sameLanguage.first()
    return picked["Index"]?.jsonPrimitive?.intOrNull
}

/**
 * The audio streams of the media source, as options the player lists when the stream itself has one track (a transcode): the key is the
 * server's stream index. Empty for a source with fewer than two. The label is the language in the device's own language; the format and
 * the channel count go to the player, which words the row's second line the way it words its own rows.
 */
internal fun JsonObject.audioOptions(): List<PlayAudioOption> {
    val streams = array("MediaStreams").filterIsInstance<JsonObject>().filter { it.string("Type") == "Audio" }
    if (streams.size < MIN_AUDIO_OPTIONS) return emptyList()
    val current = this["DefaultAudioStreamIndex"]?.jsonPrimitive?.intOrNull
    return streams.mapNotNull { stream ->
        val index = stream["Index"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val label = languageName(stream.string("Language")) ?: stream.string("Title") ?: stream.string("DisplayTitle") ?: index.toString()
        PlayAudioOption(
            key = index.toString(),
            language = stream.string("Language"),
            label = label,
            current = index == current,
            codec = audioCodecOf(stream.string("Codec"), stream.string("Profile")),
            channelCount = stream["Channels"]?.jsonPrimitive?.intOrNull,
        )
    }
}

/** The player's name for the server's codec (and profile, which tells Atmos and DTS-HD apart); null where the stream names none. */
internal fun audioCodecOf(
    codec: String?,
    profile: String?,
): AudioCodec? {
    val atmos = profile?.contains("atmos", ignoreCase = true) == true
    val dtsHd = profile?.contains("dts-hd", ignoreCase = true) == true
    return when (codec?.lowercase()) {
        null -> null
        "ac3" -> AudioCodec.DOLBY_DIGITAL
        "eac3" -> if (atmos) AudioCodec.DOLBY_ATMOS else AudioCodec.DOLBY_DIGITAL_PLUS
        "truehd" -> if (atmos) AudioCodec.DOLBY_ATMOS else AudioCodec.DOLBY_TRUEHD
        "dts" -> if (dtsHd) AudioCodec.DTS_HD else AudioCodec.DTS
        "aac" -> AudioCodec.AAC
        "mp3" -> AudioCodec.MP3
        "opus" -> AudioCodec.OPUS
        "flac" -> AudioCodec.FLAC
        "vorbis" -> AudioCodec.VORBIS
        else -> AudioCodec.OTHER
    }
}

private const val MIN_AUDIO_OPTIONS = 2

private fun languageName(tag: String?): String? {
    val code = languageCode(tag) ?: return null
    return Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() && it != code }
}

private fun JsonObject.named(label: String): Boolean =
    listOfNotNull(string("DisplayTitle"), string("Title")).any {
        it.contains(label, ignoreCase = true) ||
            label.contains(it, ignoreCase = true)
    }

/**
 * A language as one three-letter code, whether the tag is a two-letter or a three-letter one, and whichever of a language's two
 * three-letter codes the file uses; null for none or one that names no language.
 */
internal fun languageCode(tag: String?): String? {
    val clean = tag?.trim()?.lowercase().orEmpty()
    if (clean.isEmpty() || clean == "und" || clean == "zxx") return null
    val code =
        try {
            Locale.forLanguageTag(clean).isO3Language.ifEmpty { null }
        } catch (_: MissingResourceException) {
            null
        }
    return TERMINOLOGY_CODES[code ?: clean] ?: code ?: clean.takeIf { it.length == THREE_LETTERS }
}

private const val THREE_LETTERS = 3

/** The bibliographic three-letter codes files use, to the terminology code `Locale` gives. */
private val TERMINOLOGY_CODES =
    mapOf(
        "alb" to "sqi",
        "arm" to "hye",
        "baq" to "eus",
        "bur" to "mya",
        "chi" to "zho",
        "cze" to "ces",
        "dut" to "nld",
        "fre" to "fra",
        "geo" to "kat",
        "ger" to "deu",
        "gre" to "ell",
        "ice" to "isl",
        "mac" to "mkd",
        "mao" to "mri",
        "may" to "msa",
        "per" to "fas",
        "rum" to "ron",
        "slo" to "slk",
        "tib" to "bod",
        "wel" to "cym",
    )
