package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A library's `PathInfos`: one `{Path, NetworkPath}` entry per folder, inside its `LibraryOptions`. */
const val LIBRARY_PATH_INFOS = "PathInfos"

private const val PATH = "Path"
private const val NETWORK_PATH = "NetworkPath"

/** The shared network path the library's options hold for the folder at [path], or null when it has none. */
fun JsonObject.networkPath(path: String): String? =
    array(LIBRARY_PATH_INFOS)
        .firstNotNullOfOrNull { (it as? JsonObject)?.takeIf { info -> info.string(PATH) == path } }
        ?.string(NETWORK_PATH)
        ?.takeIf { it.isNotBlank() }

/**
 * The `PathInfos` list with [path]'s network path set to [networkPath], or cleared when that is blank. Every other key of the
 * folder's entry, and every other folder's entry, is kept as the server sent it. A folder with no entry gets one.
 */
fun JsonObject.withNetworkPath(
    path: String,
    networkPath: String,
): JsonArray {
    val entries = array(LIBRARY_PATH_INFOS)
    val typed = networkPath.trim()

    fun changed(info: JsonObject) = JsonObject(if (typed.isEmpty()) info - NETWORK_PATH else info + (NETWORK_PATH to JsonPrimitive(typed)))
    val found = entries.any { (it as? JsonObject)?.string(PATH) == path }
    if (!found) {
        return JsonArray(entries + changed(JsonObject(mapOf(PATH to JsonPrimitive(path)))))
    }
    return JsonArray(entries.map { if (it is JsonObject && it.string(PATH) == path) changed(it) else it })
}
