package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.serialization.json.JsonObject

/** The server itself, as `/System/Info` describes it. Admin only. */
data class ServerInfo(
    val name: String,
    val version: String,
    val address: String,
    val architecture: String,
    val operatingSystem: String,
    val updateAvailable: Boolean,
    val restartPending: Boolean,
    /** Whether the server can restart itself; one that can't has to be restarted where it runs. */
    val canRestart: Boolean,
    /** Whether the server is stopping. Absent on a release that doesn't say, which reads as no. */
    val shuttingDown: Boolean = false,
    /** Where the server keeps its files, as `/System/Info` says. */
    val paths: ServerPaths = ServerPaths(),
)

/** The folders the server reports in `/System/Info`. A path the server doesn't send is empty, and the page leaves its row out. */
data class ServerPaths(
    val programData: String = "",
    val cache: String = "",
    val itemsByName: String = "",
    val metadata: String = "",
    val transcodes: String = "",
    val logs: String = "",
    val web: String = "",
)

suspend fun JellyfinClient.serverInfo(): ServerInfo {
    val json = getObject("System/Info")
    return ServerInfo(
        name = json.string("ServerName").orEmpty(),
        version = json.string("Version").orEmpty(),
        address = json.string("LocalAddress").orEmpty(),
        architecture = json.string("SystemArchitecture").orEmpty(),
        operatingSystem = (json.string("OperatingSystemDisplayName") ?: json.string("OperatingSystem")).orEmpty(),
        updateAvailable = json.bool("HasUpdateAvailable") == true,
        restartPending = json.bool("HasPendingRestart") == true,
        canRestart = json.bool("CanSelfRestart") == true,
        shuttingDown = json.bool("IsShuttingDown") == true,
        paths =
            ServerPaths(
                programData = json.string("ProgramDataPath").orEmpty(),
                cache = json.string("CachePath").orEmpty(),
                itemsByName = json.string("ItemsByNamePath").orEmpty(),
                metadata = json.string("InternalMetadataPath").orEmpty(),
                transcodes = json.string("TranscodingTempPath").orEmpty(),
                logs = json.string("LogPath").orEmpty(),
                web = json.string("WebPath").orEmpty(),
            ),
    )
}

/** The server's own folders that `/System/Info/Storage` reports space for, by the key each is under. */
enum class StorageKind(
    val key: String,
) {
    ProgramData("ProgramDataFolder"),
    Web("WebFolder"),
    ImageCache("ImageCacheFolder"),
    Cache("CacheFolder"),
    Logs("LogFolder"),
    Metadata("InternalMetadataFolder"),
    Transcodes("TranscodingTempFolder"),
}

/** One folder and the disk it is on: its [type] is the file system ("ext4"), and null when the server doesn't say. */
data class StorageFolder(
    val path: String,
    val usedBytes: Long,
    val freeBytes: Long,
    val type: String?,
) {
    val totalBytes: Long get() = usedBytes + freeBytes

    /** How much of the disk is used, 0 to 1; null when the server gave no sizes. */
    val usedFraction: Float? get() = if (totalBytes > 0) (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else null
}

/** A library and the folders it reads from. */
data class LibraryStorage(
    val name: String,
    val folders: List<StorageFolder>,
)

/** The disk space the server reports: its own folders, then each library's. */
data class ServerStorage(
    val system: List<Pair<StorageKind, StorageFolder>>,
    val libraries: List<LibraryStorage>,
) {
    val isEmpty: Boolean get() = system.isEmpty() && libraries.none { it.folders.isNotEmpty() }
}

/**
 * The disk space of the server's folders, from `GET /System/Info/Storage` (10.11, `hasStorageInfo`). Admin only. A folder or library the
 * server describes in a way this can't read is left out, never failing the rest.
 */
suspend fun JellyfinClient.serverStorage(): ServerStorage {
    val json = getObject("System/Info/Storage")
    return ServerStorage(
        system = StorageKind.entries.mapNotNull { kind -> json.obj(kind.key)?.storageFolder()?.let { kind to it } },
        libraries =
            json.array("Libraries").mapNotNull { element ->
                val library = element as? JsonObject ?: return@mapNotNull null
                val name = library.string("Name") ?: return@mapNotNull null
                LibraryStorage(name, library.array("Folders").mapNotNull { (it as? JsonObject)?.storageFolder() })
            },
    )
}

private fun JsonObject.storageFolder(): StorageFolder? {
    val path = string("Path") ?: return null
    return StorageFolder(path, long("UsedSpace") ?: 0L, long("FreeSpace") ?: 0L, string("StorageType"))
}

/** Asks the server to restart. Everyone using it is cut off until it's back, this app's sockets included. */
suspend fun JellyfinClient.restartServer() {
    post("System/Restart", null)
}

/** Whether the server answers at all, as it does to anyone, signed in or not. */
suspend fun JellyfinClient.answers(): Boolean = attempt { get("System/Info/Public") }.isSuccess
