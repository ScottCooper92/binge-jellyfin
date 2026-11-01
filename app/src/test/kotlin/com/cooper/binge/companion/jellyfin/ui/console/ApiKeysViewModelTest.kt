package com.cooper.binge.companion.jellyfin.ui.console

import android.util.Log
import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.ApiKey
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
import org.robolectric.shadows.ShadowLog

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ApiKeysViewModelTest {
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

    private fun ApiKeysViewModel.settled(check: (ApiKeysUiState) -> Boolean = { it !is ApiKeysUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun ApiKeysViewModel.ready(check: (ApiKeysUiState.Ready) -> Boolean = { true }) =
        settled { it is ApiKeysUiState.Ready && check(it) } as ApiKeysUiState.Ready

    @Test
    fun `lists the keys newest first, each known by its last four characters`() {
        val ready = made(ApiKeysViewModel(connection)).ready()
        assertEquals(listOf("Home Assistant", "Sonarr"), ready.keys.map { it.app })
        assertEquals("2222", ready.keys.first().lastFour)
        assertNull(ready.created)
    }

    @Test
    fun `a new key is made for the trimmed app name, shown once, and gone when dismissed`() {
        val vm = made(ApiKeysViewModel(connection))
        vm.ready()
        vm.askCreate(true)
        assertTrue(vm.ready().naming)
        vm.create("  Radarr ")
        val made = vm.ready { it.created != null && !it.busy }
        assertFalse(made.naming)
        assertEquals("Radarr", made.created!!.app)
        assertEquals("newkey30000000000000", made.created!!.key)
        assertEquals(3, made.keys.size)
        assertEquals("Radarr", jellyfin.requests.last { it.url.encodedPath == "/Auth/Keys" && it.method == "POST" }.url.queryParameter("app"))
        vm.dismissCreated()
        val after = vm.ready { it.created == null }
        assertEquals(3, after.keys.size)
    }

    @Test
    fun `a blank app name asks the server for nothing`() {
        val vm = made(ApiKeysViewModel(connection))
        vm.ready()
        vm.create("   ")
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == "/Auth/Keys" })
    }

    @Test
    fun `a key that can't be made says why and shows no key`() {
        val vm = made(ApiKeysViewModel(connection))
        vm.ready()
        jellyfin.status["/Auth/Keys"] = 500
        vm.create("Radarr")
        val failed = vm.ready { it.actionFailed != null }
        assertNull(failed.created)
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
    }

    @Test
    fun `revoking asks first, then removes the key and reads the list again`() {
        val vm = made(ApiKeysViewModel(connection))
        val sonarr = vm.ready().keys.last()
        vm.askRemove(sonarr)
        assertEquals(sonarr, vm.ready().removing)
        vm.askRemove(null)
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        vm.askRemove(sonarr)
        vm.remove()
        val ready = vm.ready { it.keys.size == 1 && !it.busy }
        assertEquals(listOf("Home Assistant"), ready.keys.map { it.app })
        assertNull(ready.removing)
        assertEquals(1, jellyfin.requests.count { it.method == "DELETE" && it.url.encodedPath == "/Auth/Keys/aaaabbbbccccdddd1111" })
    }

    @Test
    fun `a revoke the server refuses leaves the key, says so, and never logs the key`() {
        val vm = made(ApiKeysViewModel(connection))
        val sonarr = vm.ready().keys.last()
        jellyfin.status["/Auth/Keys/${sonarr.key}"] = 500
        vm.askRemove(sonarr)
        vm.remove()
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(2, failed.keys.size)
        val logged =
            ShadowLog.getLogs().flatMap { listOfNotNull(it.msg, it.throwable?.message, it.throwable?.toString()) }
        assertTrue(logged.any { "Console call failed" in it || "Jellyfin answered 500" in it })
        assertTrue(logged.none { sonarr.key in it })
    }

    @Test
    fun `a list that can't be read says why, and Try again reads it`() {
        jellyfin.status["/Auth/Keys"] = 403
        val vm = made(ApiKeysViewModel(connection))
        assertEquals(ApiKeysUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Auth/Keys")
        vm.load()
        assertEquals(2, vm.ready().keys.size)
    }

    @Test
    fun `a key prints without its secret`() {
        val key = ApiKey("0123456789abcdef", "Sonarr", null)
        assertFalse("0123456789abcdef" in key.toString())
        assertTrue("cdef" in key.toString())
        Log.i("test", key.toString())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
