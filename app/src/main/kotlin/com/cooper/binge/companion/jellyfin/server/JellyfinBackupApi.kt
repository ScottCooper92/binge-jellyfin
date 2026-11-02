package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a backup archive holds. The database is what a restore is for; the rest are optional extras. */
data class BackupOptions(
    val database: Boolean = true,
    val metadata: Boolean = false,
    val subtitles: Boolean = false,
    val trickplay: Boolean = false,
)

/**
 * A backup the server holds, as `/Backup` lists it (10.11): when it was made, by which server version, and what it
 * holds. [fileName] is what a restore names it by: the server resolves it inside its own backup directory, so the
 * path on the server is never sent back.
 */
data class ServerBackup(
    val fileName: String,
    val createdMillis: Long?,
    val serverVersion: String,
    val options: BackupOptions,
)

/** The backups the server holds, newest first. The list carries each one's manifest, so nothing else is read. */
suspend fun JellyfinClient.backups(): List<ServerBackup> =
    ((get("Backup") as? JsonArray) ?: JsonArray(emptyList()))
        .mapNotNull { (it as? JsonObject)?.toBackup() }
        .sortedByDescending { it.createdMillis ?: Long.MIN_VALUE }

/**
 * Makes a backup holding [options], and answers with it. The server writes the archive before it answers, which takes
 * minutes on a big library, so this reads with [CREATE_READ_TIMEOUT_MILLIS] and not the usual 30 seconds.
 */
suspend fun JellyfinClient.createBackup(options: BackupOptions): ServerBackup {
    val body =
        JsonObject(
            mapOf(
                "Database" to JsonPrimitive(options.database),
                "Metadata" to JsonPrimitive(options.metadata),
                "Subtitles" to JsonPrimitive(options.subtitles),
                "Trickplay" to JsonPrimitive(options.trickplay),
            ),
        )
    return withReadTimeout(CREATE_READ_TIMEOUT_MILLIS).postObject("Backup/Create", body).toBackup()
        ?: throw JellyfinUnreadableAnswer("Backup/Create")
}

/**
 * Schedules a restore from [fileName] and restarts the server, which answers as soon as it has the request. The server then
 * replaces its database with the backup's, so what happened since is lost, and so may be the session that asked.
 */
suspend fun JellyfinClient.restoreBackup(fileName: String) {
    post("Backup/Restore", JsonObject(mapOf("ArchiveFileName" to JsonPrimitive(fileName))))
}

/** How long a backup may take to write before the call gives up. */
internal const val CREATE_READ_TIMEOUT_MILLIS = 600_000L

private fun JsonObject.toBackup(): ServerBackup? {
    val fileName = string("Path")?.substringAfterLast('/')?.substringAfterLast('\\')?.takeIf { it.isNotBlank() } ?: return null
    val options = obj("Options")
    return ServerBackup(
        fileName = fileName,
        createdMillis = string("DateCreated")?.epochMillisOrNull(),
        serverVersion = string("ServerVersion").orEmpty(),
        options =
            BackupOptions(
                database = options?.bool("Database") ?: true,
                metadata = options?.bool("Metadata") == true,
                subtitles = options?.bool("Subtitles") == true,
                trickplay = options?.bool("Trickplay") == true,
            ),
    )
}
