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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RepositoriesViewModelTest {
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

    private fun RepositoriesViewModel.ready(check: (RepositoriesUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(
                TIMEOUT_MILLIS,
            ) { state.first { it is RepositoriesUiState.Ready && check(it) } }
        } as RepositoriesUiState.Ready

    private fun saved() = jellyfin.lastBodyText("/Repositories")

    @Test
    fun `reads the repositories`() {
        val ready = made(RepositoriesViewModel(connection)).ready()
        assertEquals(listOf("Jellyfin Stable"), ready.repositories.map { it.name })
    }

    @Test
    fun `adding sends the whole list, the old one as it was, and the list reads back`() {
        val vm = made(RepositoriesViewModel(connection))
        vm.ready()
        vm.askAdd(true)
        assertTrue(vm.ready().adding)
        vm.add("  Mine ", "https://mine.example/manifest.json")
        val ready = vm.ready { it.repositories.size == 2 && !it.saving }
        assertFalse(ready.adding)
        assertEquals(
            """[{"Name":"Jellyfin Stable","Url":"https://repo.example/stable.json","Enabled":true,"FutureField":1},""" +
                """{"Name":"Mine","Url":"https://mine.example/manifest.json","Enabled":true}]""",
            saved(),
        )
    }

    @Test
    fun `an address that isn't a web address, or a blank name, sends nothing`() {
        val vm = made(RepositoriesViewModel(connection))
        vm.ready()
        for (bad in listOf("", "not a url", "ftp://example.com/x", "example.com/m.json")) vm.add("Mine", bad)
        vm.add("   ", "https://mine.example/manifest.json")
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == "/Repositories" })
        assertEquals("https://mine.example/manifest.json", repositoryUrl(" https://mine.example/manifest.json "))
        assertNull(repositoryUrl("mailto:me@example.com"))
    }

    @Test
    fun `removing asks first, then sends the list without it`() {
        val vm = made(RepositoriesViewModel(connection))
        val stable = vm.ready().repositories.first()
        vm.askRemove(stable)
        assertEquals(stable, vm.ready().removing)
        vm.askRemove(null)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == "/Repositories" })
        vm.askRemove(stable)
        vm.remove()
        val ready = vm.ready { it.repositories.isEmpty() && !it.saving }
        assertNull(ready.removing)
        assertEquals("[]", saved())
    }

    @Test
    fun `a save the server refuses leaves the list, and says so`() {
        val vm = made(RepositoriesViewModel(connection))
        vm.ready()
        jellyfin.status["/Repositories"] = 500
        vm.add("Mine", "https://mine.example/manifest.json")
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(1, failed.repositories.size)
        assertFalse(failed.saving)
    }

    @Test
    fun `a list that can't be read says why, and Try again reads it`() {
        jellyfin.status["/Repositories"] = 403
        val vm = made(RepositoriesViewModel(connection))
        assertEquals(
            RepositoriesUiState.Failed(ConsoleFailure.Refused),
            runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it !is RepositoriesUiState.Loading } } },
        )
        jellyfin.status.remove("/Repositories")
        vm.load()
        assertEquals(1, vm.ready().repositories.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
