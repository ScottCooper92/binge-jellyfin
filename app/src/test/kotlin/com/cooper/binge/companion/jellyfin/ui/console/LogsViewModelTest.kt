package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LogsViewModelTest {
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

    private fun LogsViewModel.settled(check: (LogsUiState) -> Boolean) = runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun LogsViewModel.ready(check: (LogsUiState.Ready) -> Boolean = { true }) =
        settled { it is LogsUiState.Ready && check(it) } as LogsUiState.Ready

    private fun LogsViewModel.opened(check: (OpenLog) -> Boolean = { it.content !is LogContent.Loading }) =
        ready { it.open?.let(check) == true }.open!!

    @Test
    fun `lists the log files, newest first`() {
        val vm = made(LogsViewModel(connection))
        assertEquals(listOf("log_20261031.log", "log_20261030.log"), vm.ready().files.map { it.name })
        assertEquals(
            4096L,
            vm
                .ready()
                .files
                .first()
                .sizeBytes,
        )
    }

    @Test
    fun `opening a log reads its end with keys blanked, and closing returns to the list`() {
        val vm = made(LogsViewModel(connection))
        vm.ready()
        vm.open("log_20261031.log")
        val open = vm.opened()
        val content = open.content as LogContent.Ready
        assertFalse(content.truncated)
        assertTrue(content.lines.any { "api_key=[redacted]" in it })
        assertFalse(content.lines.any { "abcdef123456" in it })
        assertTrue(content.lines.any { "Logging out access token \"[redacted]\"" in it })
        assertFalse(content.lines.any { "0123456789abcdef0123456789abcdef" in it })
        vm.closeLog()
        assertEquals(null, vm.ready { it.open == null }.open)
    }

    @Test
    fun `a log naming this app's own token, or another credential, shows none of them`() {
        jellyfin.logTexts["log_20261031.log"] =
            "[10:00:00] [INF] Session for token-1 ended\n" +
            "[10:01:00] [INF] Logging out access token \"ffffeeeeddddccccbbbbaaaa99998888\"\n" +
            "[10:02:00] [INF] Revoked the token of Bob: 11112222333344445555666677778888\n"
        val vm = made(LogsViewModel(connection))
        vm.ready()
        vm.open("log_20261031.log")
        val shown = (vm.opened().content as LogContent.Ready).lines.joinToString("\n")
        for (secret in listOf("token-1", "ffffeeeeddddccccbbbbaaaa99998888", "11112222333344445555666677778888")) {
            assertFalse("$secret was shown", secret in shown)
        }
        assertEquals(3, shown.lines().size)
    }

    @Test
    fun `search narrows the lines, and so does warnings only`() {
        val vm = made(LogsViewModel(connection))
        vm.ready()
        vm.open("log_20261031.log")
        assertEquals(4, vm.opened().visible.size)
        vm.setQuery("slow")
        assertEquals(listOf("[10:05:00] [WRN] Slow query"), vm.opened { it.query == "slow" }.visible)
        vm.setQuery("")
        vm.setProblemsOnly(true)
        assertEquals(2, vm.opened { it.problemsOnly }.visible.size)
        vm.setQuery("nothing like this")
        assertEquals(emptyList<String>(), vm.opened { it.query.isNotEmpty() }.visible)
    }

    @Test
    fun `a slow read for a log that was closed is dropped`() {
        val vm = made(LogsViewModel(connection))
        vm.ready()
        jellyfin.slow += "/System/Logs/Log"
        vm.open("log_20261030.log")
        vm.closeLog()
        jellyfin.slow.clear()
        vm.open("log_20261031.log")
        val open = vm.opened()
        assertEquals("log_20261031.log", open.name)
        assertTrue((open.content as LogContent.Ready).lines.first().contains("Server started"))
    }

    @Test
    fun `a log that can't be read says why, and Try again reads it`() {
        val vm = made(LogsViewModel(connection))
        vm.ready()
        jellyfin.status["/System/Logs/Log"] = 500
        vm.open("log_20261031.log")
        assertTrue(vm.opened().content is LogContent.Failed)
        jellyfin.status.remove("/System/Logs/Log")
        vm.retryOpen()
        assertTrue(vm.opened().content is LogContent.Ready)
    }

    @Test
    fun `a list that can't be read says why, and Try again reads it`() {
        jellyfin.status["/System/Logs"] = 403
        val vm = made(LogsViewModel(connection))
        assertEquals(LogsUiState.Failed(ConsoleFailure.Refused), vm.settled { it !is LogsUiState.Loading })
        jellyfin.status.remove("/System/Logs")
        vm.load()
        assertEquals(2, vm.ready().files.size)
    }

    @Test
    fun `a line is a problem at warning level or worse`() {
        assertTrue("x [WRN] y".isProblem())
        assertTrue("x [ERR] y".isProblem())
        assertTrue("x [FTL] y".isProblem())
        assertFalse("x [INF] y".isProblem())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
