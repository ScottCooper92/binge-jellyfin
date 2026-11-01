package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ApproveCodeViewModelTest {
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

    /** Listens to [Connection.refused] for a test, and stops when it ends. */
    private val listening = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun close() {
        listening.cancel()
        teardown.close(jellyfin)
    }

    private fun ApproveCodeViewModel.resultAfter(code: String): ApproveUiState {
        onCode(code)
        approve()
        return runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it.result != null } } }
    }

    @Test
    fun `the field keeps six digits, and Approve waits for all six`() {
        val vm = made(ApproveCodeViewModel(connection))
        vm.onCode("65-43 21 9")
        assertEquals("654321", vm.state.value.code)
        vm.onCode("123")
        assertFalse(vm.state.value.complete)
        vm.approve()
        assertEquals(null, vm.state.value.result)
        assertEquals(0, jellyfin.hits("/QuickConnect/Authorize"))
    }

    @Test
    fun `a waiting code is approved and the field empties for the next`() {
        val state = made(ApproveCodeViewModel(connection)).resultAfter("654321")
        assertEquals(ApproveResult.Approved, state.result)
        assertEquals("", state.code)
    }

    @Test
    fun `a code the server is not waiting on says so and stays to be corrected`() {
        val state = made(ApproveCodeViewModel(connection)).resultAfter("111111")
        assertEquals(ApproveResult.WrongCode, state.result)
        assertEquals("111111", state.code)
    }

    @Test
    fun `Quick Connect turned off, another failure, an unreachable server and no session each say so`() {
        jellyfin.status["/QuickConnect/Authorize"] = 401
        assertEquals(ApproveResult.QuickConnectOff, made(ApproveCodeViewModel(connection)).resultAfter("654321").result)
        jellyfin.status["/QuickConnect/Authorize"] = 500
        assertEquals(ApproveResult.Failed, made(ApproveCodeViewModel(connection)).resultAfter("654321").result)
        jellyfin.close()
        assertEquals(ApproveResult.Unreachable, made(ApproveCodeViewModel(connection)).resultAfter("654321").result)
        runBlocking { sessions.clear() }
        assertEquals(ApproveResult.Unreachable, made(ApproveCodeViewModel(connection)).resultAfter("654321").result)
    }

    @Test
    fun `a 401 with the session still good is Quick Connect turned off, and nothing is reported`() {
        jellyfin.status["/QuickConnect/Authorize"] = 401
        val refused = refusals()
        assertEquals(ApproveResult.QuickConnectOff, made(ApproveCodeViewModel(connection)).resultAfter("654321").result)
        assertEquals(1, jellyfin.hits("/Users/Me"))
        assertTrue(refused.isEmpty())
    }

    @Test
    fun `a 401 the session can't pass is a refused token, reported for the home to sign in again`() {
        jellyfin.status["/QuickConnect/Authorize"] = 401
        jellyfin.status["/Users/Me"] = 401
        val refused = refusals()
        made(ApproveCodeViewModel(connection)).resultAfter("654321")
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (refused.isEmpty()) delay(POLL_MILLIS) } }
        assertEquals(1, refused.size)
    }

    /** What [Connection.refused] reports from here on. */
    private fun refusals(): MutableList<Unit> {
        val seen = CopyOnWriteArrayList<Unit>()
        listening.launch { connection.refused.collect { seen += it } }
        return seen
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
