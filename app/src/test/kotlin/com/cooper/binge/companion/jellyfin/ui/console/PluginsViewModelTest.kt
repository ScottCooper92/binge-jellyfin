package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.PluginStatus
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
class PluginsViewModelTest {
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

    private fun PluginsViewModel.ready(check: (PluginsUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is PluginsUiState.Ready && check(it) } } } as PluginsUiState.Ready

    private val aniDb = "a1b2c3d4-e5f6-0718-293a-4b5c6d7e8f90"

    @Test
    fun `reads the plugins by name, each with its status and whether it has settings`() {
        val ready = made(PluginsViewModel(connection)).ready()
        assertEquals(listOf("AniDB", "Old thing"), ready.plugins.map { it.name })
        assertEquals(PluginStatus.Active, ready.plugins[0].status)
        assertTrue(ready.plugins[0].hasSettings)
        assertEquals(PluginStatus.Superseded, ready.plugins[1].status)
        assertFalse(ready.plugins[1].hasSettings)
        assertFalse(ready.restartNeeded)
    }

    @Test
    fun `turning a plugin off sends Disable for its version and the list reads back what the server holds`() {
        val vm = made(PluginsViewModel(connection))
        val plugin = vm.ready().plugins.first()
        vm.setEnabled(plugin, on = false)
        val ready = vm.ready { it.plugins.first().status == PluginStatus.Disabled && it.busy.isEmpty() }
        assertFalse(ready.isEnabled(ready.plugins.first()))
        assertEquals(1, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == "/Plugins/$aniDb/1.2.3.4/Disable" })
        vm.setEnabled(ready.plugins.first(), on = true)
        vm.ready { it.plugins.first().status == PluginStatus.Active && it.busy.isEmpty() }
        assertEquals(1, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == "/Plugins/$aniDb/1.2.3.4/Enable" })
    }

    @Test
    fun `the switch follows what was last sent while the server only says Restart`() {
        jellyfin.pluginsReportRestart = true
        val vm = made(PluginsViewModel(connection))
        val plugin = vm.ready().plugins.first()
        vm.setEnabled(plugin, on = false)
        var ready = vm.ready { it.plugins.first().status == PluginStatus.Restart && it.busy.isEmpty() }
        assertFalse(ready.isEnabled(ready.plugins.first()))
        vm.setEnabled(ready.plugins.first(), on = true)
        ready = vm.ready { it.busy.isEmpty() && it.sentEnabled.values.all { on -> on } && it.sentEnabled.isNotEmpty() }
        assertTrue(ready.isEnabled(ready.plugins.first()))
        assertEquals(1, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath.endsWith("/Enable") })
    }

    @Test
    fun `refresh reads a plugin installed since, and the restart it needs, without going back to loading`() {
        val vm = made(PluginsViewModel(connection))
        assertFalse(vm.ready().restartNeeded)
        jellyfin.plugins +=
            """{"Name":"Kodi Sync Queue","Version":"15.0.0.0","Description":"","Id":"771e19d6-5385-4caa-b35d-b7bc8a3c8b0a",""" +
            """"CanUninstall":true,"HasImage":false,"Status":"Restart"}"""
        vm.refresh()
        val ready = vm.ready { it.plugins.size == 3 }
        assertTrue(ready.restartNeeded)
        assertTrue(ready.plugins.any { it.name == "Kodi Sync Queue" })
    }

    @Test
    fun `a plugin waiting for a restart says so, for the page to point at Restart`() {
        jellyfin.plugins = jellyfin.plugins.map { it.replace("\"Status\":\"Active\"", "\"Status\":\"Restart\"") }
        assertTrue(made(PluginsViewModel(connection)).ready().restartNeeded)
    }

    @Test
    fun `uninstalling asks first, then removes that version, and goes back to the list`() {
        val vm = made(PluginsViewModel(connection))
        val plugin = vm.ready().plugins.first()
        vm.open(plugin)
        assertEquals(plugin.id, vm.ready().open)
        vm.askUninstall(plugin)
        assertEquals(plugin, vm.ready().confirming)
        vm.askUninstall(null)
        assertEquals(0, jellyfin.requests.count { it.method == "DELETE" })
        vm.askUninstall(plugin)
        vm.uninstall()
        val ready = vm.ready { it.plugins.size == 1 && it.busy.isEmpty() }
        assertNull(ready.open)
        assertNull(ready.confirming)
        assertEquals(1, jellyfin.requests.count { it.method == "DELETE" && it.url.encodedPath == "/Plugins/$aniDb/1.2.3.4" })
    }

    @Test
    fun `a plugin's settings open at Jellyfin web's own page on the server, with no token in the address`() {
        val vm = made(PluginsViewModel(connection))
        vm.openSettings(vm.ready().plugins.first())
        val url = vm.ready { it.settingsUrl != null }.settingsUrl!!
        assertTrue(url.contains("/web/index.html#!/configurationpage?name=AniDB+settings"))
        assertFalse(url.contains("token-1"))
        assertFalse(url.contains("api_key", ignoreCase = true))
        vm.settingsOpened()
        assertNull(vm.ready { it.settingsUrl == null }.settingsUrl)
    }

    @Test
    fun `a plugin with no settings page says it could not be opened`() {
        jellyfin.configurationPages = "[]"
        val vm = made(PluginsViewModel(connection))
        vm.openSettings(vm.ready().plugins.first())
        val ready = vm.ready { it.actionFailed != null }
        assertNull(ready.settingsUrl)
    }

    @Test
    fun `a change the server refuses leaves the plugin as it was, and says so`() {
        val vm = made(PluginsViewModel(connection))
        val plugin = vm.ready().plugins.first()
        jellyfin.status["/Plugins/$aniDb/1.2.3.4/Disable"] = 500
        vm.setEnabled(plugin, on = false)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertEquals(PluginStatus.Active, failed.plugins.first().status)
        assertTrue(failed.busy.isEmpty())
    }

    @Test
    fun `a list that can't be read says why, and Try again reads it`() {
        jellyfin.status["/Plugins"] = 403
        val vm = made(PluginsViewModel(connection))
        assertEquals(
            PluginsUiState.Failed(ConsoleFailure.Refused),
            runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it !is PluginsUiState.Loading } } },
        )
        jellyfin.status.remove("/Plugins")
        vm.load()
        assertEquals(2, vm.ready().plugins.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
