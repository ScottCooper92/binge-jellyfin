package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskStatus

/** How a run ended, shown on its row for a moment, as binge-seerr's jobs do. */
enum class JobOutcome { Succeeded, Failed }

/** About three seconds of the server's once-a-second task pushes. */
internal const val START_GRACE = 3

/**
 * Scheduled tasks as the server pushes them, with what this page has asked of them.
 *
 * - [busy]: tasks with a run or a stop in flight.
 * - [starting]: tasks run from here that haven't been pushed as running yet, with how many idle pushes
 *   are still waited out. A task can read idle for a push or two after it's asked to run.
 * - [outcomes]: how each task's run just ended, shown for a moment.
 */
data class TaskBoard(
    val tasks: List<ServerTask>,
    val busy: Set<String> = emptySet(),
    val starting: Map<String, Int> = emptyMap(),
    val outcomes: Map<String, JobOutcome> = emptyMap(),
) {
    /** Whether [task] shows as running: the server says so, or it was just run from here. */
    fun running(task: ServerTask): Boolean = task.running || task.id in starting

    fun task(key: String): ServerTask? = tasks.firstOrNull { it.key == key }

    /** [id] was asked to run, and the server took it. */
    fun started(id: String): TaskBoard = copy(busy = busy - id, starting = starting + (id to START_GRACE), outcomes = outcomes - id)

    /** This board with the tasks the server just pushed, and the outcome of any run that ended. */
    fun following(pushed: List<ServerTask>): TaskBoard {
        var starting = starting
        var outcomes = outcomes
        for (task in pushed) {
            val before = tasks.firstOrNull { it.id == task.id }
            val finished = before != null && task.lastRun != null && task.lastRun != before.lastRun
            when {
                task.running -> starting = starting - task.id
                finished || before?.running == true -> {
                    starting = starting - task.id
                    task.lastRun
                        ?.status
                        ?.outcome()
                        ?.let { outcomes = outcomes + (task.id to it) }
                }
                task.id in starting -> {
                    val left = starting.getValue(task.id) - 1
                    starting = if (left > 0) starting + (task.id to left) else starting - task.id
                }
            }
        }
        return copy(tasks = pushed, starting = starting, outcomes = outcomes)
    }
}

/** A cancelled run is the admin's own doing, so it has no outcome to show. */
private fun TaskStatus.outcome(): JobOutcome? =
    when (this) {
        TaskStatus.Completed -> JobOutcome.Succeeded
        TaskStatus.Failed, TaskStatus.Aborted -> JobOutcome.Failed
        TaskStatus.Cancelled -> null
    }
