package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerSettings
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
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
        val ready = ServerSettingsViewModel(connection).ready()
        assertEquals(ServerSettings("truenas", "en", "US", quickConnect = true), ready.settings)
        assertEquals(Branding(), ready.branding)
        assertEquals(listOf("GB", "US"), ready.countries.map { it.code })
        assertEquals(true, ready.cultures.isNotEmpty())
    }

    @Test
    fun `a setting saves into the whole configuration, the rest as it was`() {
        val vm = ServerSettingsViewModel(connection)
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
        val vm = ServerSettingsViewModel(connection)
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
        val vm = ServerSettingsViewModel(connection)
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
        val vm = ServerSettingsViewModel(connection)
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
        val ready = ServerSettingsViewModel(connection).ready()
        assertEquals(emptyList<Any>(), ready.countries)
        assertEquals("truenas", ready.settings.name)
    }

    @Test
    fun `settings that can't be read fail the page`() {
        jellyfin.status["/System/Configuration"] = 500
        assertEquals(ServerSettingsUiState.Failed(ConsoleFailure.Refused), ServerSettingsViewModel(connection).settled())
    }

    private companion object {
        const val POLL_MILLIS = 10L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
