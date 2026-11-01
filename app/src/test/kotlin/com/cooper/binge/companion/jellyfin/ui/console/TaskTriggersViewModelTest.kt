package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TICKS_PER_HOUR
import com.cooper.binge.companion.jellyfin.server.TaskTrigger
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TaskTriggersViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun TasksViewModel.ready(check: (TasksUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is TasksUiState.Ready && check(it) } } } as TasksUiState.Ready

    private fun TasksViewModel.openScan(): TasksViewModel {
        val scan = ready().board.tasks.first { it.id == "t-scan" }
        open(scan)
        return this
    }

    private fun saved(): List<String> =
        jellyfin
            .lastBodyText("/ScheduledTasks/t-scan/Triggers")
            .let { (Json.parseToJsonElement(it) as JsonArray).map { trigger -> trigger.toString() } }

    @Test
    fun `a task comes with its triggers, and opening one shows them`() {
        val vm = made(TasksViewModel(connection)).openScan()
        val ready = vm.ready { it.open == "t-scan" }
        val triggers =
            ready.board.tasks
                .first { it.id == "t-scan" }
                .triggers
        assertEquals(listOf("DailyTrigger", "IntervalTrigger"), triggers.map { it.type })
        vm.open(null)
        assertNull(vm.ready { it.open == null }.open)
    }

    @Test
    fun `adding a trigger sends the whole list, the old ones as they were, and the page reads the new one back`() {
        val vm = made(TasksViewModel(connection)).openScan()
        vm.askAdd(true)
        assertTrue(vm.ready().adding)
        vm.addTrigger(TaskTrigger.weekly("Friday", 4 * TICKS_PER_HOUR))
        val ready =
            vm.ready {
                !it.saving &&
                    !it.adding &&
                    it.board.tasks
                        .first { t -> t.id == "t-scan" }
                        .triggers.size == 3
            }
        assertEquals(
            3,
            ready.board.tasks
                .first { it.id == "t-scan" }
                .triggers.size,
        )
        val sent = saved()
        assertEquals(3, sent.size)
        assertEquals("""{"Type":"DailyTrigger","TimeOfDayTicks":108000000000,"MaxRuntimeTicks":72000000000}""", sent[0])
        assertEquals("""{"Type":"IntervalTrigger","IntervalTicks":216000000000}""", sent[1])
        assertEquals("""{"Type":"WeeklyTrigger","TimeOfDayTicks":144000000000,"DayOfWeek":"Friday"}""", sent[2])
    }

    @Test
    fun `removing a trigger sends the list without it`() {
        val vm = made(TasksViewModel(connection)).openScan()
        vm.removeTrigger(0)
        val ready =
            vm.ready {
                !it.saving &&
                    it.board.tasks
                        .first { t -> t.id == "t-scan" }
                        .triggers.size == 1
            }
        assertEquals(
            "IntervalTrigger",
            ready.board.tasks
                .first { it.id == "t-scan" }
                .triggers
                .single()
                .type,
        )
        assertEquals(listOf("""{"Type":"IntervalTrigger","IntervalTicks":216000000000}"""), saved())
    }

    @Test
    fun `a trigger the app doesn't know, and its fields, survive a change to its task`() {
        val vm = made(TasksViewModel(connection))
        vm.ready()
        vm.open(
            vm
                .ready()
                .board.tasks
                .first { it.id == "t-clean" },
        )
        vm.addTrigger(TaskTrigger.startup())
        vm.ready {
            !it.saving &&
                it.board.tasks
                    .first { t -> t.id == "t-clean" }
                    .triggers.size == 3
        }
        val sent =
            (Json.parseToJsonElement(jellyfin.lastBodyText("/ScheduledTasks/t-clean/Triggers")) as JsonArray).map { it.toString() }
        assertEquals("""{"Type":"SystemEventTrigger","FutureField":7}""", sent[1])
    }

    @Test
    fun `a change the server refuses leaves the triggers, and says so`() {
        val vm = made(TasksViewModel(connection)).openScan()
        jellyfin.status["/ScheduledTasks/t-scan/Triggers"] = 500
        vm.removeTrigger(0)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertFalse(failed.saving)
        assertEquals(
            2,
            failed.board.tasks
                .first { it.id == "t-scan" }
                .triggers.size,
        )
    }

    @Test
    fun `with no task open, a trigger change sends nothing`() {
        val vm = made(TasksViewModel(connection))
        vm.ready()
        vm.addTrigger(TaskTrigger.startup())
        vm.removeTrigger(0)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath.endsWith("/Triggers") })
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
