package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerInfo
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
class ServerViewModelTest {
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

    private fun viewModel() = ServerViewModel(connection, pollMillis = 10, downWaitMillis = 100, backWaitMillis = 600)

    private fun ServerViewModel.settled(check: (ServerUiState) -> Boolean = { it !is ServerUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun ServerViewModel.ready(check: (ServerUiState.Ready) -> Boolean = { true }) =
        settled { it is ServerUiState.Ready && check(it) } as ServerUiState.Ready

    @Test
    fun `reads the server's info`() {
        assertEquals(
            ServerInfo("truenas", "10.11.6", "http://192.168.1.10:8096", "X64", "Linux", false, true, true),
            viewModel().ready().info,
        )
    }

    @Test
    fun `a restart asks first, then waits for the server to go down and come back, and reads it again`() {
        val vm = viewModel()
        vm.ready()
        vm.confirm(true)
        assertEquals(true, vm.ready().confirming)
        vm.confirm(false)
        vm.confirm(true)
        jellyfin.systemInfo = jellyfin.systemInfo.replace("\"HasPendingRestart\":true", "\"HasPendingRestart\":false")
        vm.restart()
        assertEquals(Restart.Restarting, vm.ready().restart)
        assertFalse(vm.ready().confirming)
        val back = vm.ready { it.restart == Restart.Restarted }
        assertFalse(back.info.restartPending)
        assertEquals(1, jellyfin.hits("/System/Restart"))
    }

    @Test
    fun `a restart too quick to see counts as back once the wait for it to go down is over`() {
        jellyfin.restartDownPolls = 0
        val vm = viewModel()
        vm.ready()
        vm.restart()
        assertEquals(Restart.Restarted, vm.ready { it.restart == Restart.Restarted }.restart)
    }

    @Test
    fun `a server that doesn't come back, or won't restart, says so`() {
        jellyfin.restartDownPolls = 1_000
        val vm = viewModel()
        vm.ready()
        vm.restart()
        assertEquals(Restart.NotBack, vm.ready { it.restart == Restart.NotBack }.restart)
        jellyfin.status["/System/Restart"] = 403
        vm.restart()
        assertEquals(Restart.Failed, vm.ready { it.restart == Restart.Failed }.restart)
    }

    @Test
    fun `a server that can't restart itself offers no restart`() {
        jellyfin.systemInfo = jellyfin.systemInfo.replace("\"CanSelfRestart\":true", "\"CanSelfRestart\":false")
        val vm = viewModel()
        vm.ready()
        vm.confirm(true)
        assertFalse(vm.ready().confirming)
        vm.restart()
        assertEquals(0, jellyfin.hits("/System/Restart"))
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/System/Info"] = 403
        val vm = viewModel()
        assertEquals(ServerUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/System/Info")
        vm.load()
        assertEquals("truenas", vm.ready().info.name)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
