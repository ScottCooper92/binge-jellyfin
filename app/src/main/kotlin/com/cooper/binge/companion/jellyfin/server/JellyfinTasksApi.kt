package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** How a task's last run ended, as `LastExecutionResult.Status` says. */
enum class TaskStatus { Completed, Failed, Cancelled, Aborted }

/** A task's last run: how it ended, when, and the server's error if it failed. */
data class TaskRun(
    val status: TaskStatus,
    val endedMillis: Long?,
    val error: String?,
)

/**
 * One of the server's scheduled tasks, as `/ScheduledTasks` lists it and its `ScheduledTasksInfo` pushes
 * report it. Admin only. [percent] is set while it runs; [stopping] while a stop is being honoured.
 */
data class ServerTask(
    val id: String,
    val key: String,
    val name: String,
    val description: String,
    val category: String,
    val running: Boolean,
    val stopping: Boolean,
    val percent: Int?,
    val lastRun: TaskRun?,
    /** When the task runs by itself; empty for one that only runs when started. */
    val triggers: List<TaskTrigger> = emptyList(),
)

/** The library scan task's key: "Scan Media Library", which `/Library/Refresh` also starts. */
const val LIBRARY_SCAN_TASK = "RefreshLibrary"

/** The tasks an admin sees in Jellyfin's own dashboard: hidden ones (Live TV's guide, say) are left out. */
suspend fun JellyfinClient.tasks(): List<ServerTask> {
    val answer = get(TASKS, mapOf("isHidden" to "false")) as? JsonArray ?: throw JellyfinUnreadableAnswer(TASKS)
    return answer.toTasks()
}

suspend fun JellyfinClient.runTask(id: String) {
    post("ScheduledTasks/Running/$id", null)
}

suspend fun JellyfinClient.stopTask(id: String) {
    delete("ScheduledTasks/Running/$id")
}

/** Every task's state as the server pushes it, on a socket of the caller's own, about once a second. */
fun JellyfinClient.taskPushes(): Flow<List<ServerTask>> = socketMessages(subscribe = listOf(TASKS_START)).mapNotNull { it.pushedTasks() }

/** A `ScheduledTasksInfo` push's tasks, hidden ones left out; null for any other message. */
internal fun JsonObject.pushedTasks(): List<ServerTask>? =
    if (string("MessageType") == "ScheduledTasksInfo") (this["Data"] as? JsonArray)?.toTasks() else null

internal fun JsonArray.toTasks(): List<ServerTask> = mapNotNull { (it as? JsonObject)?.taskOrNull() }

private fun JsonObject.taskOrNull(): ServerTask? {
    val id = string("Id") ?: return null
    if (bool("IsHidden") == true) return null
    val state = string("State")
    return ServerTask(
        id = id,
        key = string("Key").orEmpty(),
        name = string("Name").orEmpty(),
        description = string("Description").orEmpty(),
        category = string("Category").orEmpty(),
        running = state == "Running" || state == "Cancelling",
        stopping = state == "Cancelling",
        percent = runningPercent(),
        lastRun = obj("LastExecutionResult")?.taskRun(),
        triggers = (this["Triggers"] as? JsonArray)?.toTriggers().orEmpty(),
    )
}

private fun JsonObject.taskRun(): TaskRun? {
    val status = TaskStatus.entries.firstOrNull { it.name == string("Status") } ?: return null
    return TaskRun(status, string("EndTimeUtc")?.epochMillisOrNull(), string("ErrorMessage")?.takeIf { it.isNotBlank() })
}

/** A running task's progress, 0 until it reports any; null when it isn't running. */
internal fun JsonObject.runningPercent(): Int? =
    if (string("State") == "Running") (string("CurrentProgressPercentage")?.toDoubleOrNull() ?: 0.0).toInt().coerceIn(0, PERCENT) else null

private const val TASKS = "ScheduledTasks"
private const val PERCENT = 100

/** Push every task's state straight away, then every second. */
internal const val TASKS_START = """{"MessageType":"ScheduledTasksInfoStart","Data":"0,1000"}"""
