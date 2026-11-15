package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.NFO_PATH_SUBSTITUTION
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NfoViewModelTest {
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

    private fun NfoViewModel.ready(check: (NfoUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is NfoUiState.Ready && check(it) } } } as NfoUiState.Ready

    private fun saved(count: Int) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == PATH && it.method == "POST" } < count) delay(POLL_MILLIS)
            }
        }

    @Test
    fun `reads the configuration and the users to pick from`() {
        val ready = made(NfoViewModel(connection)).ready()
        assertNull(ready.config.nfoUserId())
        assertEquals(listOf("Claude", "Sam"), ready.users?.map { it.name })
    }

    @Test
    fun `a switch saves the whole object with that one change, and a key the page does not know goes back as it came`() {
        val vm = made(NfoViewModel(connection))
        vm.ready()
        vm.set(NFO_PATH_SUBSTITUTION, JsonPrimitive(false))
        saved(1)
        val sent = jellyfin.lastBody(PATH)
        assertEquals(false, sent.getValue(NFO_PATH_SUBSTITUTION).jsonPrimitive.boolean)
        assertEquals("yyyy-MM-dd", sent.getValue("ReleaseDateFormat").jsonPrimitive.content)
        assertEquals(
            "me",
            sent
                .getValue("FutureOption")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a user is saved by id and closes the picker, and none clears it`() {
        val vm = made(NfoViewModel(connection))
        vm.ready()
        vm.sheet(NfoSheet.User)
        vm.setUser("user-2")
        assertNull(vm.ready().sheet)
        saved(1)
        assertEquals(
            "user-2",
            jellyfin
                .lastBody(PATH)
                .getValue("UserId")
                .jsonPrimitive.content,
        )
        assertEquals("user-2", vm.ready().config.nfoUserId())
        vm.setUser(null)
        saved(2)
        assertEquals(JsonNull, jellyfin.lastBody(PATH).getValue("UserId"))
        assertNull(vm.ready().config.nfoUserId())
    }

    @Test
    fun `a date format is trimmed and saved, and a blank one is not sent`() {
        val vm = made(NfoViewModel(connection))
        vm.ready()
        vm.sheet(NfoSheet.DateFormat)
        vm.setDateFormat("   ")
        assertEquals(NfoSheet.DateFormat, vm.ready().sheet)
        vm.setDateFormat(" dd/MM/yyyy ")
        assertNull(vm.ready().sheet)
        saved(1)
        assertEquals(
            "dd/MM/yyyy",
            jellyfin
                .lastBody(PATH)
                .getValue("ReleaseDateFormat")
                .jsonPrimitive.content,
        )
        assertTrue(jellyfin.requests.count { it.url.encodedPath == PATH && it.method == "POST" } == 1)
    }

    @Test
    fun `a configuration the server will not give is a failure the page can retry`() {
        jellyfin.nfoConfiguration = ""
        val vm = made(NfoViewModel(connection))
        runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is NfoUiState.Failed } } }
        jellyfin.nfoConfiguration = """{"UserId":"user-1","ReleaseDateFormat":"yyyy-MM-dd"}"""
        vm.load()
        assertEquals("user-1", vm.ready().config.nfoUserId())
    }

    private companion object {
        const val PATH = "/System/Configuration/xbmcmetadata"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
