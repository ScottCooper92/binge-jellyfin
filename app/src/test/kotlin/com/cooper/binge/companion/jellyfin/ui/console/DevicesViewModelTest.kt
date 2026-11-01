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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DevicesViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private lateinit var thisDevice: String

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking {
            sessions.save(jellyfin.session())
            thisDevice = sessions.deviceId()
        }
        jellyfin.devices = jellyfin.devices +
            """{"Id":"$thisDevice","Name":"Pixel","AppName":"Jellyfin Companion","LastUserName":"Claude","DateLastActivity":"2026-10-29T10:00:00Z"}"""
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        jellyfin.close()
    }

    private fun DevicesViewModel.settled(check: (DevicesUiState) -> Boolean = { it !is DevicesUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun DevicesViewModel.ready(check: (DevicesUiState.Ready) -> Boolean = { true }) =
        settled { it is DevicesUiState.Ready && check(it) } as DevicesUiState.Ready

    @Test
    fun `reads the devices most recently seen first, knowing which is this one`() {
        val ready = DevicesViewModel(connection).ready()
        assertEquals(listOf("d-tv", "d-web", thisDevice), ready.devices.map { it.id })
        assertEquals(thisDevice, ready.thisDevice)
        assertEquals("Claude", ready.devices.first().user)
    }

    @Test
    fun `a rename saves the name, trimmed, and the row reads it back`() {
        val vm = DevicesViewModel(connection)
        val tv = vm.ready().devices.first()
        vm.askRename(tv)
        assertEquals(tv, vm.ready().renaming)
        vm.rename("  Living room ")
        val renamed = vm.ready { it.busy.isEmpty() && it.devices.first().customName != null }
        assertNull(renamed.renaming)
        assertEquals("Living room", renamed.devices.first().displayName)
        assertEquals("SHIELD", renamed.devices.first().name)
        val save = jellyfin.requests.last { it.url.encodedPath == "/Devices/Options" && it.method == "POST" }
        assertEquals("d-tv", save.url.queryParameter("id"))
        assertEquals("Living room", jellyfin.deviceNames["d-tv"])
    }

    @Test
    fun `remove asks first, then removes, and this device can't be`() {
        val vm = DevicesViewModel(connection)
        val ready = vm.ready()
        vm.askRemove(ready.devices.last())
        assertNull(vm.ready().removing)
        vm.remove()
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })

        vm.askRemove(ready.devices[1])
        vm.askRemove(null)
        assertNull(vm.ready().removing)
        vm.askRemove(ready.devices[1])
        vm.remove()
        assertEquals(listOf("d-tv", thisDevice), vm.ready { it.devices.size == 2 }.devices.map { it.id })
        assertEquals(
            "d-web",
            jellyfin.requests
                .last { it.method == "DELETE" }
                .url
                .queryParameter("id"),
        )
    }

    @Test
    fun `a change that fails says so and leaves the list as it was`() {
        val vm = DevicesViewModel(connection)
        val tv = vm.ready().devices.first()
        jellyfin.status["/Devices/Options"] = 500
        vm.askRename(tv)
        vm.rename("Den")
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertEquals("SHIELD", failed.devices.first().name)
        assertEquals(emptySet<String>(), failed.busy)
    }

    @Test
    fun `a device whose info can't be read keeps the name it gave itself`() {
        jellyfin.deviceNames["d-tv"] = "Den"
        jellyfin.status["/Devices/Info"] = 404
        assertEquals(
            "SHIELD",
            DevicesViewModel(connection)
                .ready()
                .devices
                .first()
                .name,
        )
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/Devices"] = 403
        val vm = DevicesViewModel(connection)
        assertEquals(DevicesUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Devices")
        vm.load()
        assertEquals(3, vm.ready().devices.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
