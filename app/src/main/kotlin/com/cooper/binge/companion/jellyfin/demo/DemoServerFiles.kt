package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val KEY_LENGTH = 32
private const val LOG_LINES = 60
private const val ROTATED_LOGS = 3
private const val LOG_KIB = 1024L
private const val BACKUP_DAYS_AGO = 6L
private const val RADARR_DAYS_AGO = 40L
private const val HOME_DAYS_AGO = 12L
private const val LOG_MINUTES_STEP = 3L
private const val WARNING_EVERY = 17
private const val WARNING_AT = 5
private const val MESSAGE_KINDS = 4
private const val THREADS = 9
private const val MODIFIED_STEP_MINUTES = 7L

/** The files an administrator manages on the server: backups, log files and API keys. */
internal class DemoServerFiles : DemoState {
    private val now = Instant.now()
    private val backups: MutableList<JsonObject> = seedBackups()
    private val keys: MutableList<JsonObject> = seedKeys()

    private fun seedBackups() =
        mutableListOf(
            backup(
                "jellyfin-backup-${stamp(now.minus(Duration.ofDays(BACKUP_DAYS_AGO)))}.zip",
                now.minus(Duration.ofDays(BACKUP_DAYS_AGO)),
                database = true,
            ),
        )

    private fun seedKeys() =
        mutableListOf(
            key("Radarr", "c0ffee00c0ffee00c0ffee00c0ffee01", now.minus(Duration.ofDays(RADARR_DAYS_AGO))),
            key("Home Assistant", "c0ffee00c0ffee00c0ffee00c0ffee02", now.minus(Duration.ofDays(HOME_DAYS_AGO))),
        )

    override fun reset() {
        backups.clear()
        backups.addAll(seedBackups())
        keys.clear()
        keys.addAll(seedKeys())
    }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Backup") { _, _ -> ok(backups.toList()) },
            route("POST", "Backup", "Create") { call, _ -> create(call.jsonBody()) },
            route("POST", "Backup", "Restore") { _, _ -> noContent },
            route("GET", "System", "Logs") { _, _ -> ok(logFiles()) },
            route("GET", "System", "Logs", "Log") { call, _ -> DemoAnswer(HTTP_OK, logText(call.query("name").orEmpty())) },
            route("GET", "Auth", "Keys") { _, _ -> ok(buildJsonObject { put("Items", JsonArray(keys.toList())) }) },
            route("POST", "Auth", "Keys") { call, _ -> addKey(call.query("app").orEmpty()) },
            route("DELETE", "Auth", "Keys", "{}") { _, (token) ->
                if (keys.removeAll {
                        it.string("AccessToken") == token
                    }
                ) {
                    noContent
                } else {
                    demoNotFound
                }
            },
        )

    private fun create(options: JsonObject): DemoAnswer {
        val created = backup("jellyfin-backup-${stamp(Instant.now())}.zip", Instant.now(), options.bool("Database") != false, options)
        backups += created
        return ok(created)
    }

    private fun addKey(app: String): DemoAnswer {
        keys +=
            key(
                app.ifBlank { "Unnamed" },
                UUID
                    .randomUUID()
                    .toString()
                    .replace("-", "")
                    .take(KEY_LENGTH),
                Instant.now(),
            )
        return noContent
    }

    private fun logFiles(): List<JsonObject> =
        (0 until ROTATED_LOGS).map { n ->
            val day = now.minus(Duration.ofDays(n.toLong()))
            buildJsonObject {
                put("Name", "log_${DAY.format(day)}.log")
                put("Size", LOG_KIB * LOG_KIB * (n + 1) / 2)
                put("DateModified", day.minus(Duration.ofMinutes(n * MODIFIED_STEP_MINUTES)).toString())
            }
        }

    private fun logText(name: String): String =
        (0 until LOG_LINES).joinToString("\n") { n ->
            val at = now.minus(Duration.ofMinutes((LOG_LINES - n) * LOG_MINUTES_STEP))
            val level = if (n % WARNING_EVERY == WARNING_AT) "WRN" else "INF"
            val message =
                when (n % MESSAGE_KINDS) {
                    0 -> "Emby.Server.Implementations.Library.LibraryMonitor: Watching directory /media/movies"
                    1 -> "Jellyfin.Api.Auth.CustomAuthenticationHandler: Authenticated $name request"
                    2 -> "Emby.Server.Implementations.ScheduledTasks.TaskManager: Scan media library Completed after 12 seconds"
                    else -> "Jellyfin.Server.Implementations.Users.UserManager: Authentication request for Alex has succeeded"
                }
            "[${LOG_TIME.format(at)}] [$level] [${n % THREADS + 1}] $message"
        }

    private fun backup(
        file: String,
        created: Instant,
        database: Boolean,
        options: JsonObject = JsonObject(emptyMap()),
    ) = buildJsonObject {
        put("Path", "/config/data/backups/$file")
        put("DateCreated", created.toString())
        put("ServerVersion", DEMO_VERSION)
        putJsonObject("Options") {
            put("Database", database)
            put("Metadata", options.bool("Metadata") == true)
            put("Subtitles", options.bool("Subtitles") == true)
            put("Trickplay", options.bool("Trickplay") == true)
        }
    }

    private fun key(
        app: String,
        token: String,
        created: Instant,
    ) = buildJsonObject {
        put("AccessToken", token)
        put("AppName", app)
        put("DateCreated", created.toString())
    }

    private fun stamp(at: Instant) = STAMP.format(at)

    private companion object {
        const val HTTP_OK = 200
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
        val LOG_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS 'Z'").withZone(ZoneOffset.UTC)
    }
}
