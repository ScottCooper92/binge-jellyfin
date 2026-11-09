package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
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
    /** The log's long `Overview`, when it says more than [overview] (which is the short one, else this): the detail sheet shows it whole. */
    val longOverview: String? = null,
)

/** A page of the activity log, newest first, and how many entries there are in all. */
data class ActivityPage(
    val entries: List<ActivityEntry>,
    val total: Int,
)

/** How far back "from" reaches, for the presets the filter offers. */
enum class ActivityTimePreset {
    Today,
    Last7Days,
    Last30Days,
    ;

    /** The start of the span: the start of today in [zone], or 7 or 30 days before [now]. */
    fun from(
        now: Instant,
        zone: ZoneId,
    ): Instant =
        when (this) {
            Today ->
                now
                    .atZone(zone)
                    .toLocalDate()
                    .atStartOfDay(zone)
                    .toInstant()
            Last7Days -> now.minus(SEVEN_DAYS)
            Last30Days -> now.minus(THIRTY_DAYS)
        }

    private companion object {
        val SEVEN_DAYS: Duration = Duration.ofDays(7)
        val THIRTY_DAYS: Duration = Duration.ofDays(30)
    }
}

/** Whose activity: everyone's, the system's (no user), or a user's (any, or the one in [ActivityFilter.username]). */
enum class ActivityWho { Anyone, System, Users }

/**
 * What the activity log is narrowed to, all of it by the server. Every server takes a start time ([fromMillis]) and the split between
 * [ActivityWho.System] and [ActivityWho.Users]; the end time ([toMillis]), one [username], a [severity] and a [type] need Jellyfin 12
 * (`JellyfinServerProfile.hasActivityFilters`), and an older server ignores them, so the page does not offer them there.
 * [preset] is the span [fromMillis] was worked out from, for the chip that says so; it is null for a range the user picked.
 * The server matches [username], [type] and [query] as text they *contain* (`LIKE %text%`), not exactly, so "sam" also finds "samantha"; the severity is exact.
 */
data class ActivityFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val preset: ActivityTimePreset? = null,
    val who: ActivityWho = ActivityWho.Anyone,
    val username: String? = null,
    val severity: Severity? = null,
    val type: String? = null,
    /** Text the entry's title contains. Jellyfin 12 only. */
    val query: String? = null,
) {
    val isEmpty: Boolean get() = this == ActivityFilter()

    /** How many things the filter narrows by: the time, whose, severity, type and search each count once. */
    val count: Int get() =
        listOf(
            fromMillis != null || toMillis != null,
            who != ActivityWho.Anyone,
            severity != null,
            type != null,
            query != null,
        ).count { it }

    /** The query the server takes. A named user is a user's activity, so the split is left out for it. */
    fun query(): Map<String, String> =
        buildMap {
            fromMillis?.let { put("minDate", Instant.ofEpochMilli(it).toString()) }
            toMillis?.let { put("maxDate", Instant.ofEpochMilli(it).toString()) }
            val named = username?.takeIf { it.isNotBlank() }
            if (named != null) {
                put("username", named)
            } else {
                when (who) {
                    ActivityWho.Anyone -> Unit
                    ActivityWho.System -> put("hasUserId", "false")
                    ActivityWho.Users -> put("hasUserId", "true")
                }
            }
            severity?.let { put("severity", it.name) }
            type?.takeIf { it.isNotBlank() }?.let { put("type", it) }
            // The server ANDs its text parameters, so one box cannot match the title or the detail; it searches the title, which is what a row names.
            query?.takeIf { it.isNotBlank() }?.let { put("name", it) }
        }
}

/** [limit] entries of the activity log from [start], newest first, narrowed by [filter]. */
suspend fun JellyfinClient.activity(
    start: Int,
    limit: Int,
    filter: ActivityFilter = ActivityFilter(),
): ActivityPage {
    val json =
        getObject("System/ActivityLog/Entries", mapOf("startIndex" to start.toString(), "limit" to limit.toString()) + filter.query())
    val entries = json.array("Items").mapNotNull { (it as? JsonObject)?.activityEntry() }
    return ActivityPage(entries, json.int("TotalRecordCount") ?: entries.size)
}

private fun JsonObject.activityEntry(): ActivityEntry? {
    val id = long("Id") ?: return null
    val shown = (string("ShortOverview") ?: string("Overview"))?.takeIf { it.isNotBlank() }
    return ActivityEntry(
        id = id,
        name = string("Name").orEmpty(),
        overview = shown,
        longOverview = string("Overview")?.takeIf { it.isNotBlank() && it != shown },
        type = string("Type").orEmpty(),
        dateMillis = string("Date")?.epochMillisOrNull(),
        severity = severityOf(string("Severity")),
    )
}

/** Jellyfin's `Severity` is a .NET `LogLevel`, so `Critical` reads as an [Severity.Error]. */
private fun severityOf(name: String?): Severity =
    if (name == "Critical") Severity.Error else Severity.entries.firstOrNull { it.name == name } ?: Severity.Information

internal fun String.epochMillisOrNull(): Long? =
    try {
        Instant.parse(this).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
