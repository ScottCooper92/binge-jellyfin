package com.cooper.binge.companion.jellyfin.ui.search

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscardLogger
import com.cooper.binge.companion.jellyfin.server.ENCODING_AUDIO_VBR
import com.cooper.binge.companion.jellyfin.server.ENCODING_THROTTLING
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        runBlocking { withTimeout(TIMEOUT_MILLIS) { server.state.first { it is SearchConfigState.Ready } } } as SearchConfigState.Ready

    private fun SearchSwitchesViewModel.flip(
        row: String,
        on: Boolean,
    ) {
        val spec = SWITCH_SPECS.getValue(row)
        copy(spec.kind).change(spec.change(on))
    }

    private fun held(): JsonObject = Json.parseToJsonElement(jellyfin.serverConfiguration).jsonObject

    private fun awaitServer(check: (JsonObject) -> Boolean) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
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
            withTimeout(TIMEOUT_MILLIS) { vm.server.state.first { (it as? SearchConfigState.Ready)?.refused != null } }
        }
        jellyfin.status.remove("/System/Configuration")
        vm.server.refresh()
        val ready =
            runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    vm.server.state.first { it is SearchConfigState.Ready && !it.reading }
                }
            } as SearchConfigState.Ready
        assertEquals(
            original,
            ready.configuration
                .getValue("EnableMetrics")
                .jsonPrimitive.boolean,
        )
        assertFalse(ready.reading)
        assertEquals(original, held().getValue("EnableMetrics").jsonPrimitive.boolean)
    }

    private fun encodingHeld(): JsonObject = Json.parseToJsonElement(jellyfin.encodingConfiguration).jsonObject

    private fun SearchSwitchesViewModel.encodingReady() =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { encoding.state.first { it is SearchConfigState.Ready } } } as SearchConfigState.Ready

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

    private fun awaitEncoding(check: (JsonObject) -> Boolean) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (!check(encodingHeld())) delay(POLL_MILLIS)
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 30_000L
        const val POLL_MILLIS = 20L
    }
}
