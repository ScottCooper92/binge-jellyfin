package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ApproveCodeViewModelTest {
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

    private fun ApproveCodeViewModel.resultAfter(code: String): ApproveUiState {
        onCode(code)
        approve()
        return runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it.result != null } } }
    }

    @Test
    fun `the field keeps six digits, and Approve waits for all six`() {
        val vm = ApproveCodeViewModel(connection)
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
        val state = ApproveCodeViewModel(connection).resultAfter("654321")
        assertEquals(ApproveResult.Approved, state.result)
        assertEquals("", state.code)
    }

    @Test
    fun `a code the server is not waiting on says so and stays to be corrected`() {
        val state = ApproveCodeViewModel(connection).resultAfter("111111")
        assertEquals(ApproveResult.WrongCode, state.result)
        assertEquals("111111", state.code)
    }

    @Test
    fun `Quick Connect turned off, an unreachable server and no session each say so`() {
        jellyfin.status["/QuickConnect/Authorize"] = 403
        assertEquals(ApproveResult.QuickConnectOff, ApproveCodeViewModel(connection).resultAfter("654321").result)
        jellyfin.close()
        assertEquals(ApproveResult.Unreachable, ApproveCodeViewModel(connection).resultAfter("654321").result)
        runBlocking { sessions.clear() }
        assertEquals(ApproveResult.Unreachable, ApproveCodeViewModel(connection).resultAfter("654321").result)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
