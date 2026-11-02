package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LogEntry
import com.cooper.binge.companion.jellyfin.server.LogLevel
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
        assertTrue(content.entries.any { "api_key=[redacted]" in it.text })
        assertFalse(content.entries.any { "abcdef123456" in it.text })
        assertTrue(content.entries.any { "Logging out access token \"[redacted]\"" in it.message })
        assertFalse(content.entries.any { "0123456789abcdef0123456789abcdef" in it.text })
        vm.closeLog()
        assertEquals(null, vm.ready { it.open == null }.open)
    }

    @Test
    fun `a log naming this app's own token, or another credential, shows none of them`() {
        jellyfin.logTexts["log_20261031.log"] =
            "[2026-10-31 10:00:00.000 +00:00] [INF] [1] A: Session for token-1 ended\n" +
            "[2026-10-31 10:01:00.000 +00:00] [INF] [1] A: Logging out access token \"ffffeeeeddddccccbbbbaaaa99998888\"\n" +
            "[2026-10-31 10:02:00.000 +00:00] [INF] [1] A: Revoked the token of Bob: 11112222333344445555666677778888\n"
        val vm = made(LogsViewModel(connection))
        vm.ready()
        vm.open("log_20261031.log")
        val shown = (vm.opened().content as LogContent.Ready).entries.joinToString("\n") { it.text }
        for (secret in listOf("token-1", "ffffeeeeddddccccbbbbaaaa99998888", "11112222333344445555666677778888")) {
            assertFalse("$secret was shown", secret in shown)
        }
        assertEquals(3, shown.lines().size)
    }

    @Test
    fun `search narrows the entries, and so does the level`() {
        val vm = made(LogsViewModel(connection))
        vm.ready()
        vm.open("log_20261031.log")
        assertEquals(4, vm.opened().visible.size)
        vm.setQuery("slow")
        assertEquals(listOf("Slow query"), vm.opened { it.query == "slow" }.visible.map { it.message })
        vm.setQuery("")
        vm.setLevel(LogLevel.Warn)
        assertEquals(listOf(LogLevel.Warn, LogLevel.Error), vm.opened { it.level == LogLevel.Warn }.visible.map { it.level })
        vm.setLevel(LogLevel.Error)
        assertEquals(1, vm.opened { it.level == LogLevel.Error }.visible.size)
        vm.setQuery("nothing like this")
        assertEquals(emptyList<LogEntry>(), vm.opened { it.query.isNotEmpty() }.visible)
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
        assertTrue(
            (open.content as LogContent.Ready)
                .entries
                .first()
                .message
                .contains("Server started"),
        )
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

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
