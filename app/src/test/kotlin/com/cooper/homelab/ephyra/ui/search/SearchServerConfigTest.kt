package com.cooper.homelab.ephyra.ui.search

import androidx.lifecycle.ViewModel
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.ENCODING_AUDIO_VBR
import com.cooper.homelab.ephyra.server.ENCODING_THROTTLING
import com.cooper.homelab.ephyra.server.LIVE_TV_SAVE_NFO
import com.cooper.homelab.ephyra.server.NETWORK_IGNORE_VIRTUAL
import com.cooper.homelab.ephyra.server.NFO_PATH_SUBSTITUTION
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.ui.ViewModelTeardown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The search's switches for the server's configuration. It is one object that Jellyfin writes whole, and the trickplay options
 * are an object inside it, so a flip must send the copy as the search holds it with only its own key changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchServerConfigTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun started() = made(SearchSwitchesViewModel(connection)).also { it.server.start() }

    private fun SearchSwitchesViewModel.ready() =
        runBlocking { waitFor(TIMEOUT_MILLIS) { server.state.first { it is SearchConfigState.Ready } } } as SearchConfigState.Ready

    private fun SearchSwitchesViewModel.flip(
        row: String,
        on: Boolean,
    ) {
        val spec = SWITCH_SPECS.getValue(row)
        copy(spec.kind).change(spec.change(on))
    }

    /** A wait that times out says what the server had been asked (method and path only), so a failure on a loaded machine can be told apart. */
    private suspend fun <T> waitFor(
        millis: Long,
        block: suspend CoroutineScope.() -> T,
    ): T =
        try {
            withTimeout(millis, block)
        } catch (e: TimeoutCancellationException) {
            throw AssertionError("Timed out after $millis ms. The server was asked ${jellyfin.asked()}", e)
        }

    private fun held(): JsonObject = Json.parseToJsonElement(jellyfin.serverConfiguration).jsonObject

    private fun awaitServer(check: (JsonObject) -> Boolean) {
        runBlocking {
            waitFor(TIMEOUT_MILLIS) {
                while (!check(held())) delay(POLL_MILLIS)
            }
        }
    }

    @Test
    fun `a Quick Connect flip lands in the server configuration and keeps the rest`() {
        val before = held()
        val vm = started()
        vm.ready()
        vm.flip(SettingRows.SERVER_QUICK_CONNECT, false)
        awaitServer { !it.getValue("QuickConnectAvailable").jsonPrimitive.boolean }
        val after = held()
        assertEquals(before.keys, after.keys)
        assertEquals(before.getValue("TrickplayOptions"), after.getValue("TrickplayOptions"))
    }

    @Test
    fun `a trickplay flip sends the whole options object with only its own key changed`() {
        val before = held().getValue("TrickplayOptions").jsonObject
        val vm = started()
        vm.ready()
        vm.flip(SettingRows.TRICKPLAY_HW_ENCODING, !before.getValue("EnableHwEncoding").jsonPrimitive.boolean)
        awaitServer {
            it.getValue("TrickplayOptions").jsonObject.getValue("EnableHwEncoding") != before.getValue("EnableHwEncoding")
        }
        val after = held().getValue("TrickplayOptions").jsonObject
        assertEquals(before.keys, after.keys)
        assertEquals(before.filterKeys { it != "EnableHwEncoding" }, after.filterKeys { it != "EnableHwEncoding" })
    }

    @Test
    fun `two trickplay flips in a row keep both`() {
        val vm = started()
        vm.ready()
        vm.flip(SettingRows.TRICKPLAY_HW_ACCELERATION, true)
        vm.flip(SettingRows.TRICKPLAY_HW_ENCODING, true)
        awaitServer {
            val options = it.getValue("TrickplayOptions").jsonObject
            options.getValue("EnableHwAcceleration").jsonPrimitive.boolean &&
                options.getValue("EnableHwEncoding").jsonPrimitive.boolean
        }
    }

    @Test
    fun `the user copy is not read for a server switch`() {
        val vm = started()
        vm.ready()
        assertEquals(0, jellyfin.hits("/Users/Me"))
        assertEquals(1, jellyfin.hits("/System/Configuration"))
    }

    @Test
    fun `a refused flip is put back and not sent again`() {
        val original = held().getValue("EnableMetrics").jsonPrimitive.boolean
        val vm = started()
        vm.ready()
        jellyfin.status["/System/Configuration"] = 409
        vm.flip(SettingRows.ADVANCED_METRICS, !original)
        runBlocking {
            waitFor(TIMEOUT_MILLIS) { vm.server.state.first { (it as? SearchConfigState.Ready)?.refused != null } }
        }
        jellyfin.status.remove("/System/Configuration")
        vm.server.refresh()
        val ready =
            runBlocking {
                waitFor(TIMEOUT_MILLIS) {
                    vm.server.state.first { it is SearchConfigState.Ready && !it.reading }
                }
            } as SearchConfigState.Ready
        assertEquals(
            "the copy shown after the refresh; the server was asked ${jellyfin.asked()}",
            original,
            ready.configuration
                .getValue("EnableMetrics")
                .jsonPrimitive.boolean,
        )
        assertFalse(ready.reading)
        assertEquals(
            "the server's copy; it was asked ${jellyfin.asked()}",
            original,
            held().getValue("EnableMetrics").jsonPrimitive.boolean,
        )
    }

    private fun encodingHeld(): JsonObject = Json.parseToJsonElement(jellyfin.encodingConfiguration).jsonObject

    private fun SearchSwitchesViewModel.encodingReady() =
        runBlocking { waitFor(TIMEOUT_MILLIS) { encoding.state.first { it is SearchConfigState.Ready } } } as SearchConfigState.Ready

    @Test
    fun `a Transcoding flip lands in the encoding configuration and keeps the rest`() {
        val before = encodingHeld()
        val key = ENCODING_THROTTLING
        val original = before.getValue(key).jsonPrimitive.boolean
        val vm = made(SearchSwitchesViewModel(connection)).also { it.encoding.start() }
        vm.encodingReady()
        vm.flip(SettingRows.transcoding(key), !original)
        awaitEncoding { it.getValue(key).jsonPrimitive.boolean != original }
        val after = encodingHeld()
        assertEquals(before.keys, after.keys)
        assertEquals(before.filterKeys { it != key }, after.filterKeys { it != key })
        // Only the encoding configuration was read and written: the server's own was not touched.
        assertEquals(0, jellyfin.hits("/System/Configuration"))
    }

    @Test
    fun `two Transcoding flips in a row keep both`() {
        val vm = made(SearchSwitchesViewModel(connection)).also { it.encoding.start() }
        vm.encodingReady()
        val throttle = encodingHeld().getValue(ENCODING_THROTTLING).jsonPrimitive.boolean
        val vbr = encodingHeld().getValue(ENCODING_AUDIO_VBR).jsonPrimitive.boolean
        vm.flip(SettingRows.transcoding(ENCODING_THROTTLING), !throttle)
        vm.flip(SettingRows.transcoding(ENCODING_AUDIO_VBR), !vbr)
        awaitEncoding {
            it.getValue(ENCODING_THROTTLING).jsonPrimitive.boolean != throttle &&
                it.getValue(ENCODING_AUDIO_VBR).jsonPrimitive.boolean != vbr
        }
    }

    @Test
    fun `the search reads the encoding configuration to know which rows the page draws`() {
        val read = runBlocking { connection.encodingForSearch() }
        assertEquals(encodingHeld(), read)
    }

    @Test
    fun `the search offers everything when the encoding configuration cannot be read`() {
        jellyfin.status["/System/Configuration/encoding"] = 500
        assertEquals(null, runBlocking { connection.encodingForSearch() })
    }

    private fun networkHeld(): JsonObject = Json.parseToJsonElement(jellyfin.networkConfiguration).jsonObject

    @Test
    fun `a network flip lands in the network configuration and keeps the rest`() {
        val before = networkHeld()
        val original = before.getValue(NETWORK_IGNORE_VIRTUAL).jsonPrimitive.boolean
        val vm = made(SearchSwitchesViewModel(connection)).also { it.network.start() }
        runBlocking { waitFor(TIMEOUT_MILLIS) { vm.network.state.first { it is SearchConfigState.Ready } } }
        vm.flip(SettingRows.network(NETWORK_IGNORE_VIRTUAL), !original)
        runBlocking {
            waitFor(TIMEOUT_MILLIS) {
                while (networkHeld().getValue(NETWORK_IGNORE_VIRTUAL).jsonPrimitive.boolean == original) delay(POLL_MILLIS)
            }
        }
        val after = networkHeld()
        // Nothing else moved: the certificate password and an option this app does not know go back as they were read.
        assertEquals(before.filterKeys { it != NETWORK_IGNORE_VIRTUAL }, after.filterKeys { it != NETWORK_IGNORE_VIRTUAL })
        assertEquals(0, jellyfin.hits("/System/Configuration"))
    }

    private fun liveTvHeld(): JsonObject = Json.parseToJsonElement(jellyfin.liveTvConfiguration).jsonObject

    @Test
    fun `a Live TV flip lands in the Live TV configuration and keeps the tuners and guide providers`() {
        val before = liveTvHeld()
        val original = before.getValue(LIVE_TV_SAVE_NFO).jsonPrimitive.boolean
        val vm = made(SearchSwitchesViewModel(connection)).also { it.liveTv.start() }
        runBlocking { waitFor(TIMEOUT_MILLIS) { vm.liveTv.state.first { it is SearchConfigState.Ready } } }
        vm.flip(SettingRows.liveTv(LIVE_TV_SAVE_NFO), !original)
        runBlocking {
            waitFor(TIMEOUT_MILLIS) {
                while (liveTvHeld().getValue(LIVE_TV_SAVE_NFO).jsonPrimitive.boolean == original) delay(POLL_MILLIS)
            }
        }
        val after = liveTvHeld()
        // The tuner hosts, the guide providers with their password and a key this app does not know go back as they were read.
        assertEquals(before.filterKeys { it != LIVE_TV_SAVE_NFO }, after.filterKeys { it != LIVE_TV_SAVE_NFO })
        assertEquals(0, jellyfin.hits("/System/Configuration"))
    }

    private fun nfoHeld(): JsonObject = Json.parseToJsonElement(jellyfin.nfoConfiguration).jsonObject

    @Test
    fun `an NFO flip lands in the NFO configuration and keeps the rest`() {
        val before = nfoHeld()
        val original = before.getValue(NFO_PATH_SUBSTITUTION).jsonPrimitive.boolean
        val vm = made(SearchSwitchesViewModel(connection)).also { it.nfo.start() }
        runBlocking { waitFor(TIMEOUT_MILLIS) { vm.nfo.state.first { it is SearchConfigState.Ready } } }
        vm.flip(SettingRows.nfo(NFO_PATH_SUBSTITUTION), !original)
        runBlocking {
            waitFor(TIMEOUT_MILLIS) {
                while (nfoHeld().getValue(NFO_PATH_SUBSTITUTION).jsonPrimitive.boolean == original) delay(POLL_MILLIS)
            }
        }
        // The user, the date format and a key this app does not know go back as they were read.
        assertEquals(before.filterKeys { it != NFO_PATH_SUBSTITUTION }, nfoHeld().filterKeys { it != NFO_PATH_SUBSTITUTION })
        assertEquals(0, jellyfin.hits("/System/Configuration"))
    }

    private fun brandingHeld(): JsonObject = Json.parseToJsonElement(jellyfin.brandingConfiguration).jsonObject

    @Test
    fun `a splash screen flip lands in the branding and sends the splash image's location back`() {
        jellyfin.brandingConfiguration =
            """{"LoginDisclaimer":"hello","CustomCss":"a{}","SplashscreenEnabled":false,"SplashscreenLocation":"/config/splash.png"}"""
        val before = brandingHeld()
        val vm = made(SearchSwitchesViewModel(connection)).also { it.branding.start() }
        runBlocking { waitFor(TIMEOUT_MILLIS) { vm.branding.state.first { it is SearchConfigState.Ready } } }
        vm.flip(SettingRows.SERVER_SPLASHSCREEN, true)
        runBlocking {
            waitFor(TIMEOUT_MILLIS) {
                while (!brandingHeld().getValue("SplashscreenEnabled").jsonPrimitive.boolean) delay(POLL_MILLIS)
            }
        }
        assertEquals(before.filterKeys { it != "SplashscreenEnabled" }, brandingHeld().filterKeys { it != "SplashscreenEnabled" })
        // What the server kept is not proof the search sent it: the body it posted carries the location itself.
        val posted = jellyfin.requests.last { it.method == "POST" && it.url.encodedPath == "/System/Configuration/branding" }
        assertTrue(
            posted.body
                ?.utf8()
                .orEmpty()
                .contains("\"SplashscreenLocation\":\"/config/splash.png\""),
        )
        assertEquals(0, jellyfin.hits("/System/Configuration"))
    }

    private fun awaitEncoding(check: (JsonObject) -> Boolean) {
        runBlocking {
            waitFor(TIMEOUT_MILLIS) {
                while (!check(encodingHeld())) delay(POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 20L
    }
}
