package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TaskRun
import com.cooper.binge.companion.jellyfin.server.TaskStatus
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TasksViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Binge Jellyfin", "Test", "1.0", Dispatchers.IO)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        jellyfin.close()
    }

    private fun TasksViewModel.settled(check: (TasksUiState) -> Boolean = { it !is TasksUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun TasksViewModel.ready(check: (TasksUiState.Ready) -> Boolean = { true }) =
        settled { it is TasksUiState.Ready && check(it) } as TasksUiState.Ready

    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(TIMEOUT_MILLIS) { while (!condition()) delay(POLL_MILLIS) } }

    private fun hit(method: String) =
        jellyfin.requests.any { it.url.encodedPath == "/ScheduledTasks/Running/t-clean" && it.method == method }

    @Test
    fun `reads the tasks the dashboard shows, with how each last ran`() {
        val tasks = TasksViewModel(connection).ready().board.tasks
        assertEquals(listOf("t-scan", "t-clean"), tasks.map { it.id })
        assertEquals(TaskRun(TaskStatus.Failed, 1_793_329_200_000L, "Disk full"), tasks[1].lastRun)
        assertEquals("Maintenance", tasks[1].category)
    }

    @Test
    fun `run shows the task running from the tap, follows its pushes, and shows how it ended`() {
        val vm = TasksViewModel(connection).apply { outcomeMillis = OUTCOME_MILLIS }
        val clean = vm.ready().board.tasks[1]
        waitFor { jellyfin.socketMessages.any { "ScheduledTasksInfoStart" in it } }
        vm.run(clean)
        waitFor { hit("POST") }
        val started = vm.ready { clean.id in it.board.starting }
        assertTrue(started.board.running(started.board.tasks[1]))
        jellyfin.push(push(state = "Running", percent = 25.0, status = "Failed", ended = "2026-10-30T03:00:00Z"))
        assertEquals(
            25,
            vm
                .ready {
                    it.board.tasks
                        .getOrNull(1)
                        ?.percent == 25
                }.board.tasks[1]
                .percent,
        )
        jellyfin.push(push(state = "Idle", percent = null, status = "Completed", ended = "2026-10-31T21:00:00Z"))
        assertEquals(JobOutcome.Succeeded, vm.ready { clean.id in it.board.outcomes }.board.outcomes[clean.id])
        vm.ready { it.board.outcomes.isEmpty() }
    }

    @Test
    fun `stop asks the server to stop the run`() {
        val vm = TasksViewModel(connection)
        val clean = vm.ready().board.tasks[1]
        vm.stop(clean)
        waitFor { hit("DELETE") }
        assertEquals(emptySet<String>(), vm.ready { it.board.busy.isEmpty() }.board.busy)
    }

    @Test
    fun `a run that can't be sent says so`() {
        val vm = TasksViewModel(connection)
        val clean = vm.ready().board.tasks[1]
        jellyfin.status["/ScheduledTasks/Running/t-clean"] = 500
        vm.run(clean)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertEquals(emptySet<String>(), failed.board.busy)
        assertEquals(emptyMap<String, Int>(), failed.board.starting)
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/ScheduledTasks"] = 403
        val vm = TasksViewModel(connection)
        assertEquals(TasksUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/ScheduledTasks")
        vm.load()
        assertEquals(
            2,
            vm
                .ready()
                .board.tasks.size,
        )
    }

    private fun push(
        state: String,
        percent: Double?,
        status: String,
        ended: String,
    ): String {
        val progress = percent?.let { ""","CurrentProgressPercentage":$it""" }.orEmpty()
        return """{"MessageType":"ScheduledTasksInfo","Data":[""" +
            """{"Id":"t-scan","Key":"RefreshLibrary","Name":"Scan Media Library","Category":"Library","State":"Idle"},""" +
            """{"Id":"t-clean","Key":"CleanCache","Name":"Clean Cache Directory","Category":"Maintenance","State":"$state"$progress,""" +
            """"LastExecutionResult":{"Status":"$status","EndTimeUtc":"$ended"}}]}"""
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
        const val OUTCOME_MILLIS = 300L
    }
}
