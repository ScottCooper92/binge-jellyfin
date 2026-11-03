package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The key of a library's per-type fetcher choices: one entry for each item type that has made a choice. */
const val LIBRARY_TYPE_OPTIONS = "TypeOptions"

/**
 * The two lists of fetchers an item type keeps: who it downloads metadata from, and where it gets images. A library lists the
 * ones that are on ([enabledKey]); the server's defaults list the ones that are off ([disabledKey]). Both keep an [orderKey].
 */
enum class FetcherKind(
    val enabledKey: String,
    val disabledKey: String,
    val orderKey: String,
) {
    Metadata("MetadataFetchers", "DisabledMetadataFetchers", "MetadataFetcherOrder"),
    Images("ImageFetchers", "DisabledImageFetchers", "ImageFetcherOrder"),
}

private fun JsonObject.typeEntry(type: String): JsonObject? =
    array(LIBRARY_TYPE_OPTIONS).firstOrNull { (it as? JsonObject)?.string("Type").equals(type, ignoreCase = true) } as? JsonObject

/**
 * [kind]'s fetchers for [type] as the library has them, in its order: those its entry lists first, then the rest the server offers.
 * A library with no entry for the type has each on as the server offers it. An entry missing [kind]'s key is a state the server
 * never writes (it writes all four arrays); this reads it the same way, as a defensive choice and not as the server's own rule.
 * A fetcher the server no longer offers is left out.
 */
fun JsonObject.fetchers(
    type: FetcherType,
    kind: FetcherKind,
): List<Provider> {
    val available = type.of(kind)
    val entry = typeEntry(type.type)
    val chosen = entry?.takeIf { kind.enabledKey in it }?.names(kind.enabledKey)
    val order = entry?.names(kind.orderKey).orEmpty()
    val ordered = order.mapNotNull { name -> available.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    return (ordered + available.filterNot { fetcher -> ordered.any { it.name == fetcher.name } })
        .distinctBy { it.name }
        .map { Provider(it.name, chosen?.has(it.name) ?: it.defaultEnabled) }
}

/**
 * The `TypeOptions` array with [type]'s entry changed: [change] gets [kind]'s list as the library has it and returns the list it
 * should have. The entry written holds **both** kinds' lists in full, as jellyfin-web writes it, because an entry that exists
 * means the library has chosen: leaving a list out would turn that kind's fetchers all off. Every other key of the entry (the
 * image options) and every other type's entry go back as they were.
 */
fun JsonObject.withFetchers(
    type: FetcherType,
    kind: FetcherKind,
    change: (List<Provider>) -> List<Provider>,
): JsonArray {
    fun list(of: FetcherKind) = if (of == kind) change(fetchers(type, of)) else fetchers(type, of)
    val entry =
        JsonObject(
            (typeEntry(type.type) ?: JsonObject(mapOf("Type" to JsonPrimitive(type.type)))).toMap() +
                FetcherKind.entries.flatMap { of ->
                    val providers = list(of)
                    listOf(
                        of.enabledKey to JsonArray(providers.filter { it.enabled }.map { JsonPrimitive(it.name) }),
                        of.orderKey to JsonArray(providers.map { JsonPrimitive(it.name) }),
                    )
                },
        )
    val entries = array(LIBRARY_TYPE_OPTIONS)
    val at = entries.indexOfFirst { (it as? JsonObject)?.string("Type").equals(type.type, ignoreCase = true) }
    return JsonArray(if (at >= 0) entries.toMutableList().also { it[at] = entry } else entries + entry)
}
