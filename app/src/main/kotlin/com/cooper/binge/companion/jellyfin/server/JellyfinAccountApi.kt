package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * When Jellyfin turns subtitles on, as its user configuration stores it. [wire] is the server's own
 * name for each mode (`SubtitlePlaybackMode`).
 */
enum class SubtitleMode(
    val wire: String,
) {
    Default("Default"),
    Smart("Smart"),
    OnlyForced("OnlyForced"),
    Always("Always"),
    None("None"),
    ;

    companion object {
        /** A mode this app doesn't know reads as [Default], the server's own default. */
        fun of(wire: String?): SubtitleMode = entries.firstOrNull { it.wire == wire } ?: Default
    }
}

/**
 * The user's own track choices. Jellyfin picks audio and subtitle tracks with these when it answers
 * PlaybackInfo, so they reach Binge's player through the playback source. A language is a
 * three-letter code as the server stores it; empty means any.
 */
data class PlaybackLanguages(
    val audio: String,
    val subtitles: String,
    val subtitleMode: SubtitleMode,
)

/**
 * A language the server offers: its English name, its tag (`fr`, or `fr-ca` for a regional variant),
 * and every three-letter code it answers to.
 */
data class Culture(
    val displayName: String,
    val twoLetter: String,
    val codes: List<String>,
) {
    /** The code the server stores for this language. */
    val code: String get() = codes.first()
}

internal const val AUDIO_LANGUAGE = "AudioLanguagePreference"
internal const val SUBTITLE_LANGUAGE = "SubtitleLanguagePreference"
internal const val SUBTITLE_MODE = "SubtitleMode"

/** The signed-in user's whole configuration, kept whole so a save sends back what it didn't change. */
suspend fun JellyfinClient.userConfiguration(): JsonObject = getObject("Users/Me").obj("Configuration") ?: JsonObject(emptyMap())

/** Replaces [userId]'s configuration with [configuration]. Jellyfin has no partial update. */
suspend fun JellyfinClient.saveUserConfiguration(
    userId: String,
    configuration: JsonObject,
) {
    post("Users/Configuration", configuration, mapOf("userId" to userId))
}

/** The languages the server can choose between, as `/Localization/Cultures` lists them. */
suspend fun JellyfinClient.cultures(): List<Culture> {
    val answer = get(CULTURES) as? JsonArray ?: throw JellyfinUnreadableAnswer(CULTURES)
    return answer
        .mapNotNull { element ->
            val json = element as? JsonObject ?: return@mapNotNull null
            val codes =
                (
                    listOfNotNull(json.string("ThreeLetterISOLanguageName")) +
                        json.array("ThreeLetterISOLanguageNames").mapNotNull { (it as? JsonPrimitive)?.content }
                ).filter { it.isNotBlank() }
                    .distinct()
            val name = json.string("DisplayName") ?: json.string("Name")
            if (codes.isEmpty() || name.isNullOrBlank()) null else Culture(name, json.string("TwoLetterISOLanguageName").orEmpty(), codes)
        }.distinctBy { it.code }
}

private const val CULTURES = "Localization/Cultures"

fun JsonObject.playbackLanguages(): PlaybackLanguages =
    PlaybackLanguages(
        audio = string(AUDIO_LANGUAGE).orEmpty(),
        subtitles = string(SUBTITLE_LANGUAGE).orEmpty(),
        subtitleMode = SubtitleMode.of(string(SUBTITLE_MODE)),
    )

/** This configuration with [key] set to [value], everything else as it was. */
fun JsonObject.with(
    key: String,
    value: String,
): JsonObject = JsonObject(this + (key to JsonPrimitive(value)))
