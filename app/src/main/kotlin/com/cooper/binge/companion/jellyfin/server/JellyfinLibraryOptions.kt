package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The keys of a library's `LibraryOptions`, as the server's own `LibraryOptions` class names them
// (read at the release tags; docs/server-compatibility.md says which release added each one). The page
// changes one key at a time and sends the whole object back, so a key this app doesn't know survives.

const val LIBRARY_ENABLED = "Enabled"
const val LIBRARY_REALTIME = "EnableRealtimeMonitor"
const val LIBRARY_SAVE_LOCAL = "SaveLocalMetadata"
const val LIBRARY_REFRESH_DAYS = "AutomaticRefreshIntervalDays"
const val LIBRARY_LANGUAGE = "PreferredMetadataLanguage"
const val LIBRARY_COUNTRY = "MetadataCountryCode"
const val LIBRARY_EMBEDDED_TITLES = "EnableEmbeddedTitles"
const val LIBRARY_EMBEDDED_EPISODES = "EnableEmbeddedEpisodeInfos"
const val LIBRARY_SERIES_GROUPING = "EnableAutomaticSeriesGrouping"
const val LIBRARY_SEASON_ZERO = "SeasonZeroDisplayName"
const val LIBRARY_PHOTOS = "EnablePhotos"
const val LIBRARY_COLLECTIONS = "AutomaticallyAddToCollection"
const val LIBRARY_SKIP_EMBEDDED_SUBTITLES = "SkipSubtitlesIfEmbeddedSubtitlesPresent"
const val LIBRARY_SKIP_AUDIO_MATCH = "SkipSubtitlesIfAudioTrackMatches"
const val LIBRARY_PERFECT_MATCH = "RequirePerfectSubtitleMatch"
const val LIBRARY_SAVE_SUBTITLES = "SaveSubtitlesWithMedia"
const val LIBRARY_CHAPTER_IMAGES = "EnableChapterImageExtraction"
const val LIBRARY_CHAPTER_IMAGES_ON_SCAN = "ExtractChapterImagesDuringLibraryScan"
const val LIBRARY_TRICKPLAY = "EnableTrickplayImageExtraction"
const val LIBRARY_TRICKPLAY_ON_SCAN = "ExtractTrickplayImagesDuringLibraryScan"
const val LIBRARY_TRICKPLAY_WITH_MEDIA = "SaveTrickplayWithMedia"
const val LIBRARY_LOUDNESS = "EnableLUFSScan"
const val LIBRARY_LYRICS_WITH_MEDIA = "SaveLyricsWithMedia"

/** A list of providers a library can use: the ones switched off, and the order of the rest. */
enum class ProviderGroup(
    val disabledKey: String,
    val orderKey: String,
) {
    /** Local metadata readers (NFO files). */
    Readers("DisabledLocalMetadataReaders", "LocalMetadataReaderOrder"),
    Subtitles("DisabledSubtitleFetchers", "SubtitleFetcherOrder"),

    /** 10.10. */
    Lyrics("DisabledLyricFetchers", "LyricFetcherOrder"),

    /** 10.11: the order option is spelled `MediaSegmentProvideOrder` in 10.10, which this app leaves alone. */
    Segments("DisabledMediaSegmentProviders", "MediaSegmentProviderOrder"),
}

/** One provider as a library has it: its name on the server, and whether the library uses it. */
data class Provider(
    val name: String,
    val enabled: Boolean,
)

private fun JsonObject.names(key: String): List<String> = array(key).mapNotNull { (it as? JsonPrimitive)?.content }

private fun Collection<String>.has(name: String) = any { it.equals(name, ignoreCase = true) }

/**
 * [group]'s providers in the library's order: those it lists first, then the rest of [available] in the
 * server's order, as jellyfin-web shows them. A provider the server no longer lists is left out.
 */
fun JsonObject.providers(
    group: ProviderGroup,
    available: List<String>,
): List<Provider> {
    val order = names(group.orderKey)
    val disabled = names(group.disabledKey)
    val ordered = order.mapNotNull { name -> available.firstOrNull { it.equals(name, ignoreCase = true) } }
    return (ordered + available.filterNot { ordered.has(it) }).distinct().map { Provider(it, !disabled.has(it)) }
}

/** [group]'s order with [name] moved [by] places (-1 up, 1 down), all of [available] listed so it is kept whole. */
fun JsonObject.providerOrder(
    group: ProviderGroup,
    available: List<String>,
    name: String,
    by: Int,
): JsonArray {
    val names = providers(group, available).map { it.name }.toMutableList()
    val from = names.indexOfFirst { it.equals(name, ignoreCase = true) }
    val to = from + by
    if (from >= 0 && to in names.indices) names.add(to, names.removeAt(from))
    return JsonArray(names.map(::JsonPrimitive))
}

/** [group]'s disabled list with [name] switched to [enabled]; a disabled name the server doesn't list stays. */
fun JsonObject.providersDisabled(
    group: ProviderGroup,
    name: String,
    enabled: Boolean,
): JsonArray {
    val disabled = names(group.disabledKey).filterNot { it.equals(name, ignoreCase = true) }
    return JsonArray((if (enabled) disabled else disabled + name).map(::JsonPrimitive))
}

/** Whether the library's kind has video: its chapter images, subtitles and trickplay mean something. */
fun libraryHasVideo(kind: String): Boolean = kind in VIDEO_KINDS

/** Whether the library is of shows, or mixed content that can hold them. */
fun libraryHasShows(kind: String): Boolean = kind == "tvshows" || kind == MIXED_LIBRARY

fun libraryHasMovies(kind: String): Boolean = kind == "movies" || kind == MIXED_LIBRARY

private val VIDEO_KINDS = setOf("movies", "tvshows", "musicvideos", "homevideos", MIXED_LIBRARY)
