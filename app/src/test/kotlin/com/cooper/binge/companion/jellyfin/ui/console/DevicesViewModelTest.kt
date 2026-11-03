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

    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

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
    fun close() = teardown.close(jellyfin)

    private fun DevicesViewModel.settled(check: (DevicesUiState) -> Boolean = { it !is DevicesUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun DevicesViewModel.ready(check: (DevicesUiState.Ready) -> Boolean = { true }) =
        settled { it is DevicesUiState.Ready && check(it) } as DevicesUiState.Ready

    @Test
    fun `reads the devices most recently seen first, knowing which is this one`() {
        val ready = made(DevicesViewModel(connection)).ready()
        assertEquals(listOf("d-tv", "d-web", thisDevice), ready.devices.map { it.id })
        assertEquals(thisDevice, ready.thisDevice)
        assertEquals("Claude", ready.devices.first().user)
    }

    @Test
    fun `a pull reads the list again in place, and a pull that fails keeps the list and says so once`() {
        val vm = made(DevicesViewModel(connection))
        assertEquals(3, vm.ready().devices.size)
        jellyfin.devices = jellyfin.devices +
            """{"Id":"d-new","Name":"Tablet","AppName":"Jellyfin Android","LastUserName":"sam","DateLastActivity":"2026-11-01T10:00:00Z"}"""
        vm.refresh()
        val fresh = vm.ready { it.devices.size == 4 && !it.refreshing }
        assertNull(fresh.refreshFailed)
        jellyfin.status["/Devices"] = 500
        vm.refresh()
        val failed = vm.ready { it.refreshFailed != null && !it.refreshing }
        assertEquals(4, failed.devices.size)
        assertEquals(ConsoleFailure.Refused, failed.refreshFailed)
        // The next pull starts clean, so a second failure is said again.
        jellyfin.status.remove("/Devices")
        vm.refresh()
        assertNull(vm.ready { it.devices.size == 4 && !it.refreshing }.refreshFailed)
    }

    @Test
    fun `a rename saves the name, trimmed, and the row reads it back`() {
        val vm = made(DevicesViewModel(connection))
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
        val vm = made(DevicesViewModel(connection))
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
    fun `a rename that fails says so in the form, which stays open, and leaves the list as it was`() {
        val vm = made(DevicesViewModel(connection))
        val tv = vm.ready().devices.first()
        jellyfin.status["/Devices/Options"] = 500
        vm.askRename(tv)
        vm.rename("Den")
        val failed = vm.ready { it.formFailure != null }
        assertEquals(ConsoleFailure.Refused, failed.formFailure)
        assertEquals(tv, failed.renaming)
        assertNull(failed.actionFailed)
        assertEquals("SHIELD", failed.devices.first().name)
        assertEquals(emptySet<String>(), failed.busy)
        // A retry that works closes the form.
        jellyfin.status.remove("/Devices/Options")
        vm.rename("Den")
        val renamed = vm.ready { it.busy.isEmpty() && it.devices.first().customName != null }
        assertNull(renamed.renaming)
        assertNull(renamed.formFailure)
    }

    @Test
    fun `a sign-out the server refuses is said on the page, since it has no form`() {
        val vm = made(DevicesViewModel(connection))
        val web = vm.ready().devices.first { it.id == "d-web" }
        jellyfin.status["/Devices"] = 500
        vm.askRemove(web)
        vm.remove()
        val failed = vm.ready { it.actionFailed != null }
        assertNull(failed.formFailure)
    }

    @Test
    fun `a device whose info can't be read keeps the name it gave itself`() {
        jellyfin.deviceNames["d-tv"] = "Den"
        jellyfin.status["/Devices/Info"] = 404
        assertEquals(
            "SHIELD",
            made(DevicesViewModel(connection))
                .ready()
                .devices
                .first()
                .name,
        )
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/Devices"] = 403
        val vm = made(DevicesViewModel(connection))
        assertEquals(DevicesUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Devices")
        vm.load()
        assertEquals(3, vm.ready().devices.size)
    }

    @Test
    fun `a rename that fails after its form was left is said on the page, not lost`() {
        val vm = made(DevicesViewModel(connection))
        val tv = vm.ready().devices.first()
        vm.askRename(tv)
        val held = jellyfin.hold("/Devices/Options", method = "POST")
        vm.rename("Den")
        held.awaitArrival()
        // The form is dismissed while the call is still running, then the call fails.
        vm.askRename(null)
        held.refuse(500)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertNull(failed.formFailure)
        assertNull(failed.renaming)
        assertEquals(emptySet<String>(), failed.busy)
    }

    @Test
    fun `a rename that fails after another device's form opened is said on the page, and leaves that form alone`() {
        val vm = made(DevicesViewModel(connection))
        val devices = vm.ready().devices
        val first = devices[0]
        val second = devices[1]
        vm.askRename(first)
        val held = jellyfin.hold("/Devices/Options", method = "POST")
        vm.rename("Den")
        held.awaitArrival()
        vm.askRename(second)
        held.cut()
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Unreachable, failed.actionFailed)
        assertNull(failed.formFailure)
        assertEquals(second, failed.renaming)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
