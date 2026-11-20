package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.array
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.server.string
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
        audio = choice.audio?.let { indexOf("Audio", it) },
        subtitle = if (subtitle == null) SUBTITLES_OFF else indexOf("Subtitle", subtitle),
    )
}

/** What the server already chose for this source: its default audio, and its default subtitle, or [SUBTITLES_OFF] when it chose none. */
internal fun JsonObject.chosenIndexes(): StreamIndexes =
    StreamIndexes(
        audio = this["DefaultAudioStreamIndex"]?.jsonPrimitive?.intOrNull,
        subtitle = this["DefaultSubtitleStreamIndex"]?.jsonPrimitive?.intOrNull ?: SUBTITLES_OFF,
    )

private fun JsonObject.indexOf(
    type: String,
    choice: TrackChoice,
): Int? {
    val streams = array("MediaStreams").filterIsInstance<JsonObject>().filter { it.string("Type") == type }
    val language = choice.language?.let(::languageCode)
    val sameLanguage = if (language == null) streams else streams.filter { languageCode(it.string("Language")) == language }
    if (sameLanguage.isEmpty() || (language == null && choice.label == null)) return null
    val labelled = choice.label?.let { label -> sameLanguage.firstOrNull { it.named(label) } }
    val picked = labelled ?: sameLanguage.firstOrNull { it.bool("IsDefault") == true } ?: sameLanguage.first()
    return picked["Index"]?.jsonPrimitive?.intOrNull
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
