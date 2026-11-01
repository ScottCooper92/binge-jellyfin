package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The kinds of trigger this app can make, as the server names them. The server has no other in 10.10 and later. */
enum class TriggerKind(
    val type: String,
) {
    Daily("DailyTrigger"),
    Weekly("WeeklyTrigger"),
    Interval("IntervalTrigger"),
    Startup("StartupTrigger"),
}

/**
 * One trigger of a scheduled task, as `Triggers` lists it. Times are ticks of 100 nanoseconds. [raw] is
 * the trigger as the server sent it: a save sends it back as it was, so a trigger this app doesn't know
 * (10.8 and 10.9 have a system event trigger) and any field it doesn't read survive a save untouched.
 */
data class TaskTrigger(
    val type: String,
    val timeOfDayTicks: Long? = null,
    /** The English name the server uses (`Monday`). */
    val dayOfWeek: String? = null,
    val intervalTicks: Long? = null,
    /** How long a run may last before the server stops it; null for no limit. */
    val maxRuntimeTicks: Long? = null,
    val raw: JsonObject? = null,
) {
    /** The kind this app knows the trigger as, or null for one it doesn't. */
    val kind: TriggerKind? get() = TriggerKind.entries.firstOrNull { it.type == type }

    /** What a save sends: [raw] as it came, or, for a new trigger, only the fields its kind has. */
    fun toJson(): JsonObject =
        raw ?: JsonObject(
            buildMap {
                put("Type", JsonPrimitive(type))
                timeOfDayTicks?.let { put("TimeOfDayTicks", JsonPrimitive(it)) }
                dayOfWeek?.let { put("DayOfWeek", JsonPrimitive(it)) }
                intervalTicks?.let { put("IntervalTicks", JsonPrimitive(it)) }
                maxRuntimeTicks?.let { put("MaxRuntimeTicks", JsonPrimitive(it)) }
            },
        )

    companion object {
        /** A daily trigger at [ticks] after midnight, server time. */
        fun daily(
            ticks: Long,
            maxRuntimeTicks: Long? = null,
        ) = TaskTrigger(TriggerKind.Daily.type, timeOfDayTicks = ticks, maxRuntimeTicks = maxRuntimeTicks)

        fun weekly(
            dayOfWeek: String,
            ticks: Long,
            maxRuntimeTicks: Long? = null,
        ) = TaskTrigger(TriggerKind.Weekly.type, timeOfDayTicks = ticks, dayOfWeek = dayOfWeek, maxRuntimeTicks = maxRuntimeTicks)

        fun interval(
            ticks: Long,
            maxRuntimeTicks: Long? = null,
        ) = TaskTrigger(TriggerKind.Interval.type, intervalTicks = ticks, maxRuntimeTicks = maxRuntimeTicks)

        fun startup(maxRuntimeTicks: Long? = null) = TaskTrigger(TriggerKind.Startup.type, maxRuntimeTicks = maxRuntimeTicks)
    }
}

/** The days of the week as the server names them, Monday first. */
val TRIGGER_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

const val TICKS_PER_SECOND = 10_000_000L
const val TICKS_PER_MINUTE = 60 * TICKS_PER_SECOND
const val TICKS_PER_HOUR = 60 * TICKS_PER_MINUTE

internal fun JsonArray.toTriggers(): List<TaskTrigger> = mapNotNull { (it as? JsonObject)?.triggerOrNull() }

private fun JsonObject.triggerOrNull(): TaskTrigger? {
    val type = string("Type") ?: return null
    return TaskTrigger(
        type = type,
        timeOfDayTicks = long("TimeOfDayTicks"),
        dayOfWeek = string("DayOfWeek"),
        intervalTicks = long("IntervalTicks"),
        maxRuntimeTicks = long("MaxRuntimeTicks"),
        raw = this,
    )
}

/**
 * Replaces [id]'s triggers with [triggers]: the server takes the whole list, so a trigger left out is
 * removed. The same call, and the same body, in every release.
 */
suspend fun JellyfinClient.saveTaskTriggers(
    id: String,
    triggers: List<TaskTrigger>,
) {
    post("ScheduledTasks/$id/Triggers", JsonArray(triggers.map { it.toJson() }))
}
