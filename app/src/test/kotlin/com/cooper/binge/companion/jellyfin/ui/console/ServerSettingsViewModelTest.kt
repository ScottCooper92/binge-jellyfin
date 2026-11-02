package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerSettings
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
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
class ServerSettingsViewModelTest {
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

    private fun ServerSettingsViewModel.settled(check: (ServerSettingsUiState) -> Boolean = { it !is ServerSettingsUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun ServerSettingsViewModel.ready() = settled() as ServerSettingsUiState.Ready

    private fun saves(
        path: String,
        count: Int,
    ) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == path && it.method == "POST" } < count) delay(POLL_MILLIS)
            }
        }
    }

    @Test
    fun `reads the settings, the branding and what the pickers offer`() {
        val ready = made(ServerSettingsViewModel(connection)).ready()
        assertEquals(ServerSettings("truenas", "en", "US", quickConnect = true), ready.settings)
        assertEquals(Branding(), ready.branding)
        assertEquals(listOf("GB", "US"), ready.countries.map { it.code })
        assertEquals(true, ready.cultures.isNotEmpty())
    }

    @Test
    fun `a setting saves into the whole configuration, the rest as it was`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        vm.setMetadataCountry("GB")
        saves("/System/Configuration", 1)
        vm.setQuickConnect(false)
        saves("/System/Configuration", 2)
        val body = jellyfin.lastBody("/System/Configuration")
        assertEquals("GB", body.getValue("MetadataCountryCode").jsonPrimitive.content)
        assertEquals(false, body.getValue("QuickConnectAvailable").jsonPrimitive.booleanOrNull)
        assertEquals(false, body.getValue("EnableMetrics").jsonPrimitive.booleanOrNull)
        assertEquals("GB", vm.ready().settings.metadataCountry)
    }

    @Test
    fun `a text saves when its dialog does, and a blank server name doesn't`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        vm.edit(ServerText.Name)
        assertEquals(ServerText.Name, vm.ready().editing)
        vm.saveText(ServerText.Name, "   ")
        assertNull(vm.ready().editing)
        vm.saveText(ServerText.LoginDisclaimer, "  Family only  ")
        saves("/System/Configuration/branding", 1)
        assertEquals(
            "Family only",
            jellyfin
                .lastBody("/System/Configuration/branding")
                .getValue("LoginDisclaimer")
                .jsonPrimitive.content,
        )
        vm.saveText(ServerText.Name, " Living room ")
        saves("/System/Configuration", 1)
        assertEquals(
            "Living room",
            jellyfin
                .lastBody("/System/Configuration")
                .getValue("ServerName")
                .jsonPrimitive.content,
        )
        // The blank name before it never went.
        assertEquals(1, jellyfin.requests.count { it.url.encodedPath == "/System/Configuration" && it.method == "POST" })
    }

    @Test
    fun `the splash screen saves into the branding`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        vm.setSplashscreen(true)
        saves("/System/Configuration/branding", 1)
        assertEquals(
            true,
            jellyfin
                .lastBody("/System/Configuration/branding")
                .getValue("SplashscreenEnabled")
                .jsonPrimitive.booleanOrNull,
        )
        assertEquals(true, vm.ready().branding.splashscreen)
    }

    @Test
    fun `a save that fails puts the setting back and says so`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        jellyfin.status["/System/Configuration"] = 500
        vm.setMetadataLanguage("fr")
        val failed = vm.settled { (it as? ServerSettingsUiState.Ready)?.saveFailed != null } as ServerSettingsUiState.Ready
        assertEquals(ConsoleFailure.Refused, failed.saveFailed)
        assertEquals("en", failed.settings.metadataLanguage)
    }

    @Test
    fun `countries that can't be read leave the rest of the page`() {
        jellyfin.status["/Localization/Countries"] = 500
        val ready = made(ServerSettingsViewModel(connection)).ready()
        assertEquals(emptyList<Any>(), ready.countries)
        assertEquals("truenas", ready.settings.name)
    }

    @Test
    fun `settings that can't be read fail the page`() {
        jellyfin.status["/System/Configuration"] = 500
        assertEquals(ServerSettingsUiState.Failed(ConsoleFailure.Refused), made(ServerSettingsViewModel(connection)).settled())
    }

    private fun ServerSettingsViewModel.sheet(check: (ServerSettingsUiState.Ready) -> Boolean) =
        settled { it is ServerSettingsUiState.Ready && check(it) } as ServerSettingsUiState.Ready

    @Test
    fun `a text sheet stays open while its save runs and closes when the server takes it`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        vm.edit(ServerText.Name)
        vm.saveText(ServerText.Name, "Den")
        assertEquals(ServerText.Name, vm.ready().submitting)
        assertEquals(ServerText.Name, vm.ready().editing)
        val closed = vm.sheet { it.editing == null }
        assertNull(closed.submitting)
        assertNull(closed.sheetFailed)
        assertEquals("Den", closed.settings.name)
    }

    @Test
    fun `a text the server refuses stays in its sheet with the reason, and the setting goes back`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        jellyfin.status["/System/Configuration/branding"] = 500
        vm.edit(ServerText.CustomCss)
        vm.saveText(ServerText.CustomCss, "body { color: red }")
        val failed = vm.sheet { it.sheetFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.sheetFailed)
        assertEquals(ServerText.CustomCss, failed.editing)
        assertNull(failed.submitting)
        // The sheet says it, so the page doesn't, and the saved value is back.
        assertNull(failed.saveFailed)
        assertEquals("", failed.branding.customCss)
    }

    @Test
    fun `a text the server can't be reached for closes its sheet and the change waits on the page`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        jellyfin.dropped += "/System/Configuration"
        vm.edit(ServerText.Name)
        vm.saveText(ServerText.Name, "Den")
        val waiting = vm.sheet { it.saveFailed != null }
        assertEquals(ConsoleFailure.Unreachable, waiting.saveFailed)
        assertNull(waiting.editing)
        assertNull(waiting.submitting)
        assertEquals("Den", waiting.settings.name)
    }

    @Test
    fun `a sheet dismissed before its save is refused reports the refusal on the page`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        val held = jellyfin.hold("/System/Configuration", method = "POST")
        vm.edit(ServerText.Name)
        vm.saveText(ServerText.Name, "Den")
        held.awaitArrival()
        // The sheet is closed while the call is still running, then the call is refused.
        vm.edit(null)
        held.refuse(500)
        val failed = vm.sheet { it.saveFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.saveFailed)
        assertNull(failed.sheetFailed)
    }

    @Test
    fun `an unchanged text closes its sheet without a save`() {
        val vm = made(ServerSettingsViewModel(connection))
        vm.ready()
        vm.edit(ServerText.Name)
        vm.saveText(ServerText.Name, " truenas ")
        assertNull(vm.ready().editing)
        assertNull(vm.ready().submitting)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath.startsWith("/System/Configuration") })
    }

    private companion object {
        const val POLL_MILLIS = 10L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
