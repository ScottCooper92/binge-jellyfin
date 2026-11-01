package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One of the server's libraries, as `/Library/VirtualFolders` lists it. Admin only. */
data class ServerLibrary(
    val id: String,
    val name: String,
    /** Jellyfin's `CollectionType`: `movies`, `tvshows`, `music` and so on; empty for mixed content. */
    val type: String,
    val folders: List<String>,
    /** The library's `LibraryOptions`, whole: an option this app doesn't know is kept as it came. */
    val options: JsonObject = JsonObject(emptyMap()),
)

/** What the libraries page's socket brings. */
sealed interface LibraryPush {
    /** One library's scan, from 0 to 100, as `RefreshProgress` pushes it. */
    data class Progress(
        val id: String,
        val percent: Int,
    ) : LibraryPush

    /** Every scheduled task's state, the library scan task among them. */
    data class Tasks(
        val tasks: List<ServerTask>,
    ) : LibraryPush
}

suspend fun JellyfinClient.libraries(): List<ServerLibrary> {
    val answer = get(VIRTUAL_FOLDERS) as? JsonArray ?: throw JellyfinUnreadableAnswer(VIRTUAL_FOLDERS)
    return answer.mapNotNull { element ->
        val json = element as? JsonObject ?: return@mapNotNull null
        val id = json.string("ItemId") ?: return@mapNotNull null
        ServerLibrary(
            id = id,
            name = json.string("Name").orEmpty(),
            type = json.string("CollectionType").orEmpty(),
            folders = json.array("Locations").mapNotNull { (it as? JsonPrimitive)?.content },
            options = json.obj("LibraryOptions") ?: JsonObject(emptyMap()),
        )
    }
}

/** Scans one library for new and changed files, as Jellyfin's own "Scan library" does. */
suspend fun JellyfinClient.scanLibrary(id: String) {
    post(
        "Items/$id/Refresh",
        null,
        mapOf(
            "Recursive" to "true",
            "MetadataRefreshMode" to "Default",
            "ImageRefreshMode" to "Default",
            "ReplaceAllMetadata" to "false",
            "ReplaceAllImages" to "false",
        ),
    )
}

/**
 * The libraries page's pushes, on a socket of its own: each library's `RefreshProgress`, which the
 * server pushes to an admin unasked, and every task's state, from a `ScheduledTasksInfoStart`
 * subscription.
 */
fun JellyfinClient.libraryPushes(): Flow<LibraryPush> =
    socketMessages(subscribe = listOf(TASKS_START)).mapNotNull { message ->
        if (message.string("MessageType") == "RefreshProgress") {
            message.obj("Data")?.libraryProgress()
        } else {
            message.pushedTasks()?.let { LibraryPush.Tasks(it) }
        }
    }

private fun JsonObject.libraryProgress(): LibraryPush.Progress? {
    val id = string("ItemId") ?: return null
    val percent = string("Progress")?.toDoubleOrNull() ?: return null
    return LibraryPush.Progress(id, percent.toInt().coerceIn(0, FULL))
}

internal const val VIRTUAL_FOLDERS = "Library/VirtualFolders"
private const val FULL = 100
