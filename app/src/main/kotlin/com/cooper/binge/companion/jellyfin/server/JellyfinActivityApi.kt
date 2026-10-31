package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.format.DateTimeParseException

/** How serious an activity entry is, as its `Severity` says; anything else reads as [Information]. */
enum class Severity { Information, Warning, Error }

/** One entry of the server's activity log. Admin only. */
data class ActivityEntry(
    val id: Long,
    val name: String,
    /** A line of detail (an IP address, a client), when the server gives one. */
    val overview: String?,
    /** Jellyfin's `Type`: `SessionStarted`, `AuthenticationFailed`, `VideoPlayback` and so on. */
    val type: String,
    val dateMillis: Long?,
    val severity: Severity,
)

/** A page of the activity log, newest first, and how many entries there are in all. */
data class ActivityPage(
    val entries: List<ActivityEntry>,
    val total: Int,
)

/** [limit] entries of the activity log from [start], newest first. */
suspend fun JellyfinClient.activity(
    start: Int,
    limit: Int,
): ActivityPage {
    val json = getObject("System/ActivityLog/Entries", mapOf("startIndex" to start.toString(), "limit" to limit.toString()))
    val entries = json.array("Items").mapNotNull { (it as? JsonObject)?.activityEntry() }
    return ActivityPage(entries, json.int("TotalRecordCount") ?: entries.size)
}

private fun JsonObject.activityEntry(): ActivityEntry? {
    val id = long("Id") ?: return null
    return ActivityEntry(
        id = id,
        name = string("Name").orEmpty(),
        overview = (string("ShortOverview") ?: string("Overview"))?.takeIf { it.isNotBlank() },
        type = string("Type").orEmpty(),
        dateMillis = string("Date")?.epochMillisOrNull(),
        severity = Severity.entries.firstOrNull { it.name == string("Severity") } ?: Severity.Information,
    )
}

internal fun String.epochMillisOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
