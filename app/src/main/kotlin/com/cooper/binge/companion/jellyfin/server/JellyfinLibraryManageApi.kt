package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A kind of library the server can make, as its `CollectionType` names it. Mixed content has none. */
const val MIXED_LIBRARY = ""

/** The kinds a new library can be, in the order jellyfin-web offers them. */
val LIBRARY_KINDS = listOf("movies", "tvshows", "music", "musicvideos", "homevideos", "books", MIXED_LIBRARY)

private const val LIBRARY_OPTIONS = "Library/VirtualFolders/LibraryOptions"
private const val LIBRARY_PATHS = "Library/VirtualFolders/Paths"
private const val ENVIRONMENT = "Environment"

/**
 * Makes a library of [kind] over [path], with the server's own default options. The server scans it and
 * gives it an id, which a library made without that refresh would not have. A path with a comma can't go in the `paths` list the
 * server splits on commas, so it is added to the empty library after.
 */
suspend fun JellyfinClient.addLibrary(
    name: String,
    kind: String,
    path: String,
) {
    val query =
        buildMap {
            put("name", name)
            if (kind != MIXED_LIBRARY) put("collectionType", kind)
            if (',' !in path) put("paths", path)
            put("refreshLibrary", "true")
        }
    post("Library/VirtualFolders", buildJsonObject { putJsonObject("LibraryOptions") {} }, query)
    if (',' in path) addLibraryPath(name, path)
}

/** Removes the library and everything the server knows of it; the files stay where they are. */
suspend fun JellyfinClient.removeLibrary(name: String) {
    delete("Library/VirtualFolders", mapOf("name" to name, "refreshLibrary" to "false"))
}

/** Renames the library. The server gives the renamed one a new id, after the call: read it with [librariesWithId]. */
suspend fun JellyfinClient.renameLibrary(
    name: String,
    newName: String,
) {
    post("Library/VirtualFolders/Name", null, mapOf("name" to name, "newName" to newName, "refreshLibrary" to "true"))
}

suspend fun JellyfinClient.addLibraryPath(
    name: String,
    path: String,
) {
    val body =
        buildJsonObject {
            put("Name", name)
            putJsonObject("PathInfo") { put("Path", path) }
        }
    post(LIBRARY_PATHS, body, mapOf("refreshLibrary" to "false"))
}

suspend fun JellyfinClient.removeLibraryPath(
    name: String,
    path: String,
) {
    delete(LIBRARY_PATHS, mapOf("name" to name, "path" to path, "refreshLibrary" to "false"))
}

/** Saves a library's options: the whole object, as the server replaces what it holds. */
suspend fun JellyfinClient.saveLibraryOptions(
    id: String,
    options: JsonObject,
) {
    post(
        LIBRARY_OPTIONS,
        buildJsonObject {
            put("Id", id)
            put("LibraryOptions", options)
        },
    )
}

/** One entry of the server's file system: a drive, or a folder in one. */
data class ServerFolder(
    val name: String,
    val path: String,
)

/** The server's drives or mount points, the roots to browse from. */
suspend fun JellyfinClient.drives(): List<ServerFolder> = folders(get("$ENVIRONMENT/Drives"), "$ENVIRONMENT/Drives")

/** The folders directly inside [path] on the server, files left out. */
suspend fun JellyfinClient.folders(path: String): List<ServerFolder> {
    val answer =
        get("$ENVIRONMENT/DirectoryContents", mapOf("path" to path, "includeFiles" to "false", "includeDirectories" to "true"))
    return folders(answer, "$ENVIRONMENT/DirectoryContents")
}

private fun folders(
    answer: JsonElement,
    label: String,
): List<ServerFolder> {
    val entries = answer as? JsonArray ?: throw JellyfinUnreadableAnswer(label)
    return entries.mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        val path = json.string("Path")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        ServerFolder(json.string("Name")?.takeIf { it.isNotBlank() } ?: path, path)
    }
}

/** Whether the server has a folder at [path]. Reads only: the writable check would leave a file on the server. */
suspend fun JellyfinClient.folderExists(path: String): Boolean =
    try {
        post(
            "$ENVIRONMENT/ValidatePath",
            buildJsonObject {
                put("Path", path)
                put("IsFile", false)
            },
        )
        true
    } catch (error: JellyfinHttpException) {
        if (error.code == HTTP_NOT_FOUND) false else throw error
    }

/** The providers a library of [kind] can use, by the names the server knows them by, in its default order. */
data class AvailableProviders(
    val metadataReaders: List<String> = emptyList(),
    val subtitleFetchers: List<String> = emptyList(),
    val lyricFetchers: List<String> = emptyList(),
    val segmentProviders: List<String> = emptyList(),
)

suspend fun JellyfinClient.availableProviders(kind: String): AvailableProviders {
    val query = if (kind == MIXED_LIBRARY) emptyMap() else mapOf("libraryContentType" to kind)
    val answer = getObject("Libraries/AvailableOptions", query)

    fun names(key: String) = answer.array(key).mapNotNull { (it as? JsonObject)?.string("Name") }
    return AvailableProviders(names("MetadataReaders"), names("SubtitleFetchers"), names("LyricFetchers"), names("MediaSegmentProviders"))
}

private const val HTTP_NOT_FOUND = 404

internal fun jsonStrings(values: Collection<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))
