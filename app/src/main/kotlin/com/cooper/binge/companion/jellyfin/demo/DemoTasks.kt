package com.cooper.binge.companion.jellyfin.demo

import com.binge.companion.sdk.attempt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant

private const val TICKS_PER_HOUR = 36_000_000_000L
private const val SCAN_HOURS = 3L
private const val DAY_HOURS = 24L
private const val SCAN_MINUTES_AGO = 40L
private const val OLD_HOURS_AGO = 9L

/** One scheduled task as the sample server defines it. */
private class TaskSeed(
    val id: String,
    val key: String,
    val name: String,
    val description: String,
    val category: String,
    val triggers: String,
    val failed: Boolean = false,
)

private val SEEDS =
    listOf(
        TaskSeed(
            "t1",
            "RefreshLibrary",
            "Scan media library",
            "Scans your media library for new files and refreshes metadata.",
            "Library",
            """[{"Type":"IntervalTrigger","IntervalTicks":${TICKS_PER_HOUR * SCAN_HOURS}}]""",
        ),
        TaskSeed(
            "t2",
            "CleanLogs",
            "Clean log directory",
            "Deletes log files that are more than three days old.",
            "Maintenance",
            """[{"Type":"DailyTrigger","TimeOfDayTicks":18000000000}]""",
        ),
        TaskSeed(
            "t3",
            "CleanCache",
            "Clean cache directory",
            "Deletes cache files no longer needed by the system.",
            "Maintenance",
            """[{"Type":"DailyTrigger","TimeOfDayTicks":18000000000}]""",
        ),
        TaskSeed(
            "t4",
            "CleanTranscode",
            "Clean transcode directory",
            "Deletes transcode files more than one day old.",
            "Maintenance",
            """[{"Type":"IntervalTrigger","IntervalTicks":${TICKS_PER_HOUR * DAY_HOURS}}]""",
            failed = true,
        ),
        TaskSeed(
            "t5",
            "UpdatePlugins",
            "Update plugins",
            "Downloads and installs updates for plugins that are set to update automatically.",
            "Application",
            """[{"Type":"DailyTrigger","TimeOfDayTicks":30000000000}]""",
        ),
        TaskSeed(
            "t6",
            "OptimizeDatabase",
            "Optimise database",
            "Compacts the database and truncates free space.",
            "Maintenance",
            """[{"Type":"WeeklyTrigger","DayOfWeek":"Sunday","TimeOfDayTicks":36000000000}]""",
        ),
        TaskSeed(
            "t7",
            "TrickplayImages",
            "Generate trickplay images",
            "Creates trickplay images for videos in libraries that have it enabled.",
            "Library",
            """[{"Type":"DailyTrigger","TimeOfDayTicks":10800000000}]""",
        ),
        TaskSeed(
            "t8",
            "ExtractKeyframes",
            "Keyframe extractor",
            "Extracts keyframes from video files to create more exact HLS playlists.",
            "Library",
            "[]",
        ),
    )

/** The scheduled tasks. Running one finishes at once, so the page shows it just ran; its triggers can be changed. */
internal class DemoTasks : DemoState {
    private val now = Instant.now()
    private val tasks: MutableList<JsonObject> = seed()

    private fun seed(): MutableList<JsonObject> = SEEDS.map(::task).toMutableList()

    override fun reset() {
        tasks.clear()
        tasks.addAll(seed())
    }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "ScheduledTasks") { _, _ -> ok(tasks.toList()) },
            route("POST", "ScheduledTasks", "Running", "{}") { _, (id) -> run(id) },
            route("DELETE", "ScheduledTasks", "Running", "{}") { _, _ -> noContent },
            route("POST", "ScheduledTasks", "{}", "Triggers") { call, (id) -> saveTriggers(id, call) },
        )

    private fun run(id: String): DemoAnswer {
        val index = tasks.indexOfFirst { it.string("Id") == id }
        if (index < 0) return demoNotFound
        tasks[index] = tasks[index].with("LastExecutionResult", result("Completed", Instant.now(), null))
        return noContent
    }

    private fun saveTriggers(
        id: String,
        call: DemoCall,
    ): DemoAnswer {
        val index = tasks.indexOfFirst { it.string("Id") == id }
        if (index < 0) return demoNotFound
        val triggers = attempt { json(call.body) as? JsonArray }.getOrNull() ?: JsonArray(emptyList())
        tasks[index] = tasks[index].with("Triggers", triggers)
        return noContent
    }

    private fun task(seed: TaskSeed): JsonObject =
        buildJsonObject {
            put("Id", seed.id)
            put("Key", seed.key)
            put("Name", seed.name)
            put("Description", seed.description)
            put("Category", seed.category)
            put("State", "Idle")
            put("IsHidden", false)
            put("Triggers", json(seed.triggers))
            put(
                "LastExecutionResult",
                if (seed.failed) {
                    result("Failed", now.minus(Duration.ofHours(OLD_HOURS_AGO)), "The directory could not be read.")
                } else {
                    result("Completed", now.minus(Duration.ofMinutes(SCAN_MINUTES_AGO)), null)
                },
            )
        }

    private fun result(
        status: String,
        ended: Instant,
        error: String?,
    ): JsonElement =
        buildJsonObject {
            put("Status", status)
            put("EndTimeUtc", ended.toString())
            error?.let { put("ErrorMessage", JsonPrimitive(it)) }
        }
}
