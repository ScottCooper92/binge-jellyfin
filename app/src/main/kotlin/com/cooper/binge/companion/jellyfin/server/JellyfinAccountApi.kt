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
 * The user's own playback settings. Jellyfin picks audio and subtitle tracks with the languages when
 * it answers PlaybackInfo, so they reach any player through the server. A language is a three-letter
 * code as the server stores it; empty means any. The switches are the server's defaults (all on) until
 * read, and Jellyfin's clients honour them: the default audio track over the language, remembering a
 * track picked by hand for the rest of the series, and playing the next episode.
 */
data class PlaybackPreferences(
    val audio: String,
    val subtitles: String,
    val subtitleMode: SubtitleMode,
    val playDefaultAudioTrack: Boolean = true,
    val rememberAudio: Boolean = true,
    val rememberSubtitles: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
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
internal const val PLAY_DEFAULT_AUDIO_TRACK = "PlayDefaultAudioTrack"
internal const val REMEMBER_AUDIO = "RememberAudioSelections"
internal const val REMEMBER_SUBTITLES = "RememberSubtitleSelections"
internal const val AUTOPLAY_NEXT_EPISODE = "EnableNextEpisodeAutoPlay"

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

fun JsonObject.playbackPreferences(): PlaybackPreferences =
    PlaybackPreferences(
        audio = string(AUDIO_LANGUAGE).orEmpty(),
        subtitles = string(SUBTITLE_LANGUAGE).orEmpty(),
        subtitleMode = SubtitleMode.of(string(SUBTITLE_MODE)),
        playDefaultAudioTrack = bool(PLAY_DEFAULT_AUDIO_TRACK) != false,
        rememberAudio = bool(REMEMBER_AUDIO) != false,
        rememberSubtitles = bool(REMEMBER_SUBTITLES) != false,
        autoplayNextEpisode = bool(AUTOPLAY_NEXT_EPISODE) != false,
    )

/**
 * Changes [userId]'s password. A wrong [current] password is a 403. Jellyfin then signs the user out
 * everywhere but here: every other device's token stops working, and this one's carries on.
 */
suspend fun JellyfinClient.changePassword(
    userId: String,
    current: String,
    new: String,
) {
    post(
        "Users/Password",
        JsonObject(mapOf("CurrentPw" to JsonPrimitive(current), "NewPw" to JsonPrimitive(new))),
        mapOf("userId" to userId),
    )
}
