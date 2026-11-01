package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// The library display and metadata defaults in the server's configuration (`/System/Configuration`),
// as the server's own `ServerConfiguration` names them. Each key is in every release from 10.8 except
// the three `docs/server-compatibility.md` gates: collections (split in 10.11) and the two chapter image
// settings (10.9).

internal const val DISPLAY_FOLDER_VIEW = "EnableFolderView"
internal const val DISPLAY_SPECIALS_IN_SEASONS = "DisplaySpecialsWithinSeasons"
internal const val DISPLAY_EXTERNAL_SUGGESTIONS = "EnableExternalContentInSuggestions"
internal const val DISPLAY_COLLECTIONS = "EnableGroupingIntoCollections"
internal const val DISPLAY_COLLECTIONS_MOVIES = "EnableGroupingMoviesIntoCollections"
internal const val DISPLAY_COLLECTIONS_SHOWS = "EnableGroupingShowsIntoCollections"
internal const val DISPLAY_UI_CULTURE = "UICulture"
internal const val METADATA_HIDDEN = "SaveMetadataHidden"
internal const val METADATA_CONVENTION = "ImageSavingConvention"
internal const val METADATA_PATH = "MetadataPath"
internal const val METADATA_CHAPTER_RESOLUTION = "ChapterImageResolution"
internal const val METADATA_DUMMY_CHAPTER = "DummyChapterDuration"
internal const val SORT_REMOVE_WORDS = "SortRemoveWords"
internal const val SORT_REMOVE_CHARACTERS = "SortRemoveCharacters"
internal const val SORT_REPLACE_CHARACTERS = "SortReplaceCharacters"
internal const val PATH_SUBSTITUTIONS = "PathSubstitutions"

/** The two ways the server names the images it saves beside media; the default first. */
val IMAGE_SAVING_CONVENTIONS = listOf("Legacy", "Compatible")

/** The sizes chapter images can be made at, as the server names them, smallest last. */
val CHAPTER_RESOLUTIONS = listOf("MatchSource", "P2160", "P1440", "P1080", "P720", "P480", "P360", "P240", "P144")

/** A language the server's own pages can be shown in, as `/Localization/Options` lists it. */
data class UiLanguage(
    val name: String,
    val value: String,
)

suspend fun JellyfinClient.uiLanguages(): List<UiLanguage> {
    val answer = get("Localization/Options") as? JsonArray ?: throw JellyfinUnreadableAnswer("Localization/Options")
    return answer.mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        val value = json.string("Value")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        UiLanguage(json.string("Name")?.takeIf { it.isNotBlank() } ?: value, value)
    }
}

/** A word, character or substitution a list setting holds: one value, or a from and to pair. */
data class ListEntry(
    val first: String,
    val second: String? = null,
)

/** The lists the page edits: the three sort lists hold words, the substitutions hold pairs. */
enum class DisplayList(
    val key: String,
    val pairs: Boolean,
) {
    RemoveWords(SORT_REMOVE_WORDS, false),
    RemoveCharacters(SORT_REMOVE_CHARACTERS, false),
    ReplaceCharacters(SORT_REPLACE_CHARACTERS, false),
    Substitutions(PATH_SUBSTITUTIONS, true),
}

/** [list]'s entries in the configuration, in order; an entry that isn't one is left out. */
fun JsonObject.entries(list: DisplayList): List<ListEntry> =
    array(list.key).mapNotNull { element ->
        if (list.pairs) {
            val json = element as? JsonObject ?: return@mapNotNull null
            ListEntry(json.string("From").orEmpty(), json.string("To").orEmpty())
        } else {
            (element as? JsonPrimitive)?.content?.let { ListEntry(it) }
        }
    }

/** [entries] as the JSON array the server stores for [list]. */
fun jsonEntries(
    list: DisplayList,
    entries: List<ListEntry>,
): JsonArray =
    JsonArray(
        entries.map { entry ->
            if (list.pairs) {
                buildJsonObject {
                    put("From", entry.first)
                    put("To", entry.second.orEmpty())
                }
            } else {
                JsonPrimitive(entry.first)
            }
        },
    )
