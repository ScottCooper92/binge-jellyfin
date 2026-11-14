package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

private const val ENTRIES = 48
private const val DEFAULT_LIMIT = 50
private const val KIND_STRIDE = 3
private const val KIND_REPEAT = 7
private const val MINUTES_BETWEEN = 37L
private const val FIRST_MINUTES = 4L

/** What one kind of entry says, and how often the sample log repeats it. */
private class Kind(
    val type: String,
    val severity: String,
    val title: (String) -> String,
    val detail: (String) -> String?,
    val system: Boolean = false,
)

private val KINDS =
    listOf(
        Kind("SessionStarted", "Information", { "$it is online from Pixel" }, { "Ephyra on Pixel" }),
        Kind("AuthenticationSucceeded", "Information", { "$it successfully authenticated" }, { "Signed in from 192.168.1.20" }),
        Kind("VideoPlayback", "Information", { "$it is playing The Long Voyage" }, { "Living room TV" }),
        Kind("VideoPlaybackStopped", "Information", { "$it has finished playing Harbour Lights" }, { "Tablet" }),
        Kind("AuthenticationFailed", "Warning", { "Failed login attempt for $it" }, { "Wrong password from 203.0.113.9" }),
        Kind("ScheduledTaskCompleted", "Information", { "Scan media library completed" }, { null }, system = true),
        Kind("PluginUpdated", "Information", { "AniDB was updated" }, { "Version 9.0.0.0" }, system = true),
        Kind("TaskFailed", "Error", { "Clean transcode directory failed" }, { "The directory could not be read." }, system = true),
    )

/** The server's activity log, newest first: a week of sample entries, narrowed by the same filters the server takes. */
internal class DemoActivity(
    private val users: DemoUsers,
) : DemoState {
    private val now = Instant.now()
    private val entries: List<JsonObject> by lazy { build() }

    override fun reset() = Unit

    override fun routes(): List<DemoRoute> = listOf(route("GET", "System", "ActivityLog", "Entries") { call, _ -> ok(page(call)) })

    private fun build(): List<JsonObject> {
        val names = users.names()
        return (0 until ENTRIES).map { n ->
            val kind = KINDS[(n * KIND_STRIDE + n / KIND_REPEAT) % KINDS.size]
            val user = if (kind.system) null else names[n % names.size]
            buildJsonObject {
                put("Id", (ENTRIES - n).toLong())
                put("Name", kind.title(user ?: "System"))
                put("Type", kind.type)
                put("Severity", kind.severity)
                put("Date", now.minus(Duration.ofMinutes(MINUTES_BETWEEN * n + FIRST_MINUTES)).toString())
                kind.detail(user.orEmpty())?.let {
                    put("ShortOverview", it)
                    put("Overview", it)
                }
                put("UserId", user?.let(users::idOf) ?: "00000000000000000000000000000000")
            }
        }
    }

    private fun page(call: DemoCall): JsonObject {
        val matching = entries.filter { it.matches(call) }
        val start = call.query("startIndex")?.toIntOrNull() ?: 0
        val limit = call.query("limit")?.toIntOrNull() ?: DEFAULT_LIMIT
        return buildJsonObject {
            put("Items", JsonArray(matching.drop(start).take(limit)))
            put("TotalRecordCount", matching.size)
            put("StartIndex", start)
        }
    }

    private fun JsonObject.matches(call: DemoCall): Boolean = inTimeRange(call) && aboutUsers(call) && textMatches(call)

    private fun JsonObject.inTimeRange(call: DemoCall): Boolean {
        val date = string("Date")?.let(Instant::parse)
        val from = call.query("minDate")?.let(Instant::parse)
        val to = call.query("maxDate")?.let(Instant::parse)
        return (from == null || (date != null && date >= from)) && (to == null || (date != null && date <= to))
    }

    private fun JsonObject.aboutUsers(call: DemoCall): Boolean {
        val userId = string("UserId").orEmpty()
        val hasUser = userId.any { it != '0' }
        val named = call.query("username")?.takeIf { it.isNotBlank() }
        val userFilter = call.query("hasUserId")?.let { it.toBoolean() == hasUser } ?: true
        val nameFilter = named?.let { n -> users.names().any { it.contains(n, ignoreCase = true) && users.idOf(it) == userId } } ?: true
        return userFilter && nameFilter
    }

    private fun JsonObject.textMatches(call: DemoCall): Boolean {
        val severity = call.query("severity")?.let { it.equals(string("Severity"), ignoreCase = true) } ?: true
        val type = call.query("type")?.takeIf { it.isNotBlank() }?.let { string("Type").orEmpty().contains(it, ignoreCase = true) } ?: true
        val name = call.query("name")?.takeIf { it.isNotBlank() }?.let { string("Name").orEmpty().contains(it, ignoreCase = true) } ?: true
        return severity && type && name
    }
}
