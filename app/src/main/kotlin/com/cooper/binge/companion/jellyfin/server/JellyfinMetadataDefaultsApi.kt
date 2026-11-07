package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// The server's per-item-type metadata defaults: `MetadataOptions` in `/System/Configuration`, an array of one entry per item
// type. Read at v10.9.11 and v10.11.0 (and present at 10.8.13 and 10.10.0): each entry holds `ItemType`, the disabled and order
// lists of the two fetcher kinds, and the lists the page leaves alone (`DisabledMetadataSavers`, `LocalMetadataReaderOrder`). A fetcher
// is on unless it is in the entry's disabled list, and a type with no entry has everything on. The page changes one list of one
// entry and sends the whole configuration back, so every other entry and key survives.

const val METADATA_OPTIONS = "MetadataOptions"
const val METADATA_ITEM_TYPE = "ItemType"

/** The fetchers the server offers an item type, by the names it knows them by, in its default order. */
data class TypeFetchers(
    val type: String,
    val metadata: List<String> = emptyList(),
    val images: List<String> = emptyList(),
) {
    fun of(kind: FetcherKind): List<String> = if (kind == FetcherKind.Metadata) metadata else images
}

/**
 * The content types whose item types make up everything a library can hold, as `libraryContentType` names them. The server
 * lists the fetchers for a content type's own item types only, and a call without one covers just series, seasons, episodes
 * and movies.
 */
private val CONTENT_TYPES = listOf("movies", "tvshows", "music", "books", "homevideos", "boxsets", "playlists")

/**
 * The fetchers the server offers for each item type a library can hold, from `/Libraries/AvailableOptions`, one read for each
 * content type, together. A read that fails leaves its types without names, and never fails the rest: the page works from the
 * names the defaults themselves hold.
 */
suspend fun JellyfinClient.typeFetchers(): List<TypeFetchers> =
    coroutineScope {
        CONTENT_TYPES
            .map { contentType ->
                async { attempt { getObject("Libraries/AvailableOptions", mapOf("libraryContentType" to contentType)) }.getOrNull() }
            }.flatMap { read -> read.await()?.array("TypeOptions").orEmpty() }
            .mapNotNull { element ->
                val option = element as? JsonObject ?: return@mapNotNull null
                val type = option.string("Type") ?: return@mapNotNull null
                TypeFetchers(type, option.optionNames("MetadataFetchers"), option.optionNames("ImageFetchers"))
            }.distinctBy { it.type.lowercase() }
    }

private fun JsonObject.optionNames(key: String): List<String> = array(key).mapNotNull { (it as? JsonObject)?.string("Name") }

private fun JsonObject.entries(): List<JsonObject> = array(METADATA_OPTIONS).filterIsInstance<JsonObject>()

private fun List<JsonObject>.entryFor(type: String): JsonObject? =
    firstOrNull {
        it.string(METADATA_ITEM_TYPE).equals(type, ignoreCase = true)
    }

/** The item types the defaults hold an entry for, in the server's order. */
fun JsonObject.metadataTypes(): List<String> = entries().mapNotNull { it.string(METADATA_ITEM_TYPE) }

/** The names [kind] has switched off for [type]: none for a type with no entry. */
fun JsonObject.disabledFetchers(
    type: String,
    kind: FetcherKind,
): List<String> = entries().entryFor(type)?.names(kind.disabledKey).orEmpty()

/**
 * [type]'s fetchers of [kind] in the order the defaults ask them: those the entry's order lists first, then the rest of
 * [offered] in the server's order, then any the defaults switch off that the server no longer offers, so a name the page cannot
 * list still shows and can be switched back on.
 */
fun JsonObject.fetchers(
    type: String,
    kind: FetcherKind,
    offered: List<String>,
): List<Provider> {
    val listed = entries().entryFor(type)?.names(kind.orderKey).orEmpty()
    return orderedProviders(listed, disabledFetchers(type, kind), offered, listStale = true)
}

/**
 * The configuration with [name] switched to [enabled] in [kind]'s list for [type], the rest as the server sent it. A type with
 * no entry gets one holding its `ItemType` and this list alone, which is the server's own default for the rest.
 */
fun JsonObject.withFetcher(
    type: String,
    kind: FetcherKind,
    name: String,
    enabled: Boolean,
): JsonObject {
    val disabled = disabledFetchers(type, kind).filterNot { it.equals(name, ignoreCase = true) }
    return withEntryList(type, kind.disabledKey, if (enabled) disabled else disabled + name)
}

/** [name]'s place among the names the server [offered] for [type]: its index and how many there are, null if it is not offered. */
fun JsonObject.fetcherPlace(
    type: String,
    kind: FetcherKind,
    offered: List<String>,
    name: String,
): Pair<Int, Int>? {
    val names = fetchers(type, kind, offered).map { it.name }.filter { offered.has(it) }
    val at = names.indexOfFirst { it.equals(name, ignoreCase = true) }
    return if (at < 0) null else at to names.size
}

/**
 * The configuration with [name] moved [by] places (-1 earlier, 1 later) in [kind]'s order for [type]. Every offered name is
 * written, so the order is kept whole whatever the server adds later; a name that cannot move changes nothing.
 */
fun JsonObject.withFetcherMoved(
    type: String,
    kind: FetcherKind,
    offered: List<String>,
    name: String,
    by: Int,
): JsonObject {
    val names = fetchers(type, kind, offered).map { it.name }.filter { offered.has(it) }
    val moved = names.moved(name, by) { it }
    if (moved === names) return this
    return withEntryList(type, kind.orderKey, moved)
}

private fun JsonObject.withEntryList(
    type: String,
    key: String,
    names: List<String>,
): JsonObject {
    val all = entries()
    val found = all.entryFor(type)
    val changed =
        JsonObject(
            (found ?: JsonObject(mapOf(METADATA_ITEM_TYPE to JsonPrimitive(type)))) + (key to JsonArray(names.map(::JsonPrimitive))),
        )
    val rewritten = if (found == null) all + changed else all.map { if (it === found) changed else it }
    return JsonObject(this + (METADATA_OPTIONS to JsonArray(rewritten)))
}
