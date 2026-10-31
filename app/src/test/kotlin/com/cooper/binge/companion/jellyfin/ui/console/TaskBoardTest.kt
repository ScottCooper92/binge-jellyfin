package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.TaskRun
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBoardTest {
    private val idle = ServerTask("t", "Key", "Task", "", "", running = false, stopping = false, percent = null, lastRun = null)

    private fun ran(
        status: TaskStatus,
        at: Long,
    ) = idle.copy(lastRun = TaskRun(status, at, null))

    private val running = idle.copy(running = true, percent = 40)

    @Test
    fun `a run that ends shows how it went`() {
        val board = TaskBoard(listOf(idle)).following(listOf(running))
        assertNull(board.outcomes["t"])
        assertEquals(JobOutcome.Succeeded, board.following(listOf(ran(TaskStatus.Completed, 2))).outcomes["t"])
        assertEquals(JobOutcome.Failed, board.following(listOf(ran(TaskStatus.Failed, 2))).outcomes["t"])
        assertEquals(JobOutcome.Failed, board.following(listOf(ran(TaskStatus.Aborted, 2))).outcomes["t"])
        // Stopping it was the admin's own doing.
        assertNull(board.following(listOf(ran(TaskStatus.Cancelled, 2))).outcomes["t"])
    }

    @Test
    fun `a task run from here shows as running through a few idle pushes while it starts`() {
        var board = TaskBoard(listOf(idle), busy = setOf("t")).started("t")
        assertFalse("t" in board.busy)
        repeat(START_GRACE - 1) {
            board = board.following(listOf(idle))
            assertTrue(board.running(board.tasks.single()))
        }
        board = board.following(listOf(idle))
        assertFalse(board.running(board.tasks.single()))
    }

    @Test
    fun `a run too quick to be seen running still shows its outcome, from its new last run`() {
        val before = TaskBoard(listOf(ran(TaskStatus.Completed, 1))).started("t")
        val after = before.following(listOf(ran(TaskStatus.Completed, 2)))
        assertEquals(JobOutcome.Succeeded, after.outcomes["t"])
        assertFalse(after.running(after.tasks.single()))
    }

    @Test
    fun `a task that starts running stops waiting out idle pushes`() {
        val board = TaskBoard(listOf(idle)).started("t").following(listOf(running))
        assertEquals(emptyMap<String, Int>(), board.starting)
        assertEquals("t", board.task("Key")?.id)
    }
}
