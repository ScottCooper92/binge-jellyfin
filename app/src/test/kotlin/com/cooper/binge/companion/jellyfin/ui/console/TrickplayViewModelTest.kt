package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TrickplaySettings
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
class TrickplayViewModelTest {
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

    private fun TrickplayViewModel.settled(check: (TrickplayUiState) -> Boolean = { it !is TrickplayUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun TrickplayViewModel.ready(check: (TrickplayUiState.Ready) -> Boolean = { true }) =
        settled { it is TrickplayUiState.Ready && check(it) } as TrickplayUiState.Ready

    private fun saves(count: Int) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == CONFIGURATION && it.method == "POST" } < count) delay(POLL_MILLIS)
            }
        }
    }

    private fun options(): JsonObject = jellyfin.lastBody(CONFIGURATION).getValue("TrickplayOptions").jsonObject

    @Test
    fun `reads the options as the server has them`() {
        assertEquals(TrickplaySettings(), made(TrickplayViewModel(connection)).ready().settings)
    }

    @Test
    fun `a switch saves the whole options object with that one change, and the configuration around it as it was`() {
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.setHwEncoding(true)
        saves(1)
        val body = jellyfin.lastBody(CONFIGURATION)
        assertEquals("truenas", body.getValue("ServerName").jsonPrimitive.content)
        assertEquals(true, options().getValue("EnableHwEncoding").jsonPrimitive.boolean)
        assertEquals(false, options().getValue("EnableHwAcceleration").jsonPrimitive.boolean)
        assertEquals(90, options().getValue("JpegQuality").jsonPrimitive.int)
        assertEquals(12, options().size)
        assertTrue(vm.ready().settings.hwEncoding)
    }

    @Test
    fun `an option this app doesn't know is sent back as it was`() {
        jellyfin.serverConfiguration = """{"ServerName":"x","TrickplayOptions":{"Interval":10000,"FutureOption":7}}"""
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.setHwAcceleration(true)
        saves(1)
        assertEquals(7, options().getValue("FutureOption").jsonPrimitive.int)
    }

    @Test
    fun `a server before key frame extraction is never sent the option`() {
        jellyfin.serverConfiguration = """{"ServerName":"x","TrickplayOptions":{"Interval":10000,"EnableHwEncoding":false}}"""
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.setHwEncoding(true)
        saves(1)
        assertFalse("EnableKeyFrameOnlyExtraction" in options())
        vm.setKeyFrameOnly(true)
        saves(2)
        assertEquals(true, options().getValue("EnableKeyFrameOnlyExtraction").jsonPrimitive.boolean)
    }

    @Test
    fun `scan behaviour and priority take only the values the server has`() {
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.setScanBehavior("Sideways")
        vm.setProcessPriority("RealTime")
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == CONFIGURATION })
        vm.setScanBehavior("Blocking")
        saves(1)
        vm.setProcessPriority("Idle")
        saves(2)
        assertEquals("Blocking", options().getValue("ScanBehavior").jsonPrimitive.content)
        assertEquals("Idle", options().getValue("ProcessPriority").jsonPrimitive.content)
    }

    @Test
    fun `a number in range saves from its dialog, and one out of range keeps the dialog open and saves nothing`() {
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.edit(TrickplayField.JpegQuality)
        assertEquals(TrickplayField.JpegQuality, vm.ready().editing)
        for (bad in listOf("0", "101", "abc", "", "9.5")) vm.save(TrickplayField.JpegQuality, bad)
        assertEquals(TrickplayField.JpegQuality, vm.ready().editing)
        assertEquals(0, jellyfin.requests.count { it.method == "POST" && it.url.encodedPath == CONFIGURATION })
        vm.save(TrickplayField.JpegQuality, " 75 ")
        saves(1)
        assertNull(vm.ready().editing)
        assertEquals(75, options().getValue("JpegQuality").jsonPrimitive.int)
    }

    @Test
    fun `each number has the range Jellyfin's own page allows`() {
        assertEquals(null, TrickplayField.Qscale.parse("1"))
        assertEquals(2, TrickplayField.Qscale.parse("2"))
        assertEquals(31, TrickplayField.Qscale.parse("31"))
        assertEquals(null, TrickplayField.Qscale.parse("32"))
        assertEquals(0, TrickplayField.Threads.parse("0"))
        assertEquals(null, TrickplayField.Threads.parse("-1"))
        assertEquals(null, TrickplayField.TileWidth.parse("0"))
        assertEquals(1, TrickplayField.Interval.parse("1"))
        assertEquals(null, TrickplayField.Interval.parse("0"))
    }

    @Test
    fun `widths are read from a comma list, smallest first with repeats dropped`() {
        assertEquals(listOf(320, 640), parseWidths("640, 320,320"))
        assertEquals(listOf(320), parseWidths("320"))
        assertEquals(listOf(320, 640), parseWidths("320,, 640,"))
        assertNull(parseWidths(""))
        assertNull(parseWidths(" , "))
        assertNull(parseWidths("320, wide"))
        assertNull(parseWidths("0"))
        assertNull(parseWidths("320, 99999"))
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.save(TrickplayField.Widths, "640, 320")
        saves(1)
        assertEquals(listOf(320, 640), options().getValue("WidthResolutions").jsonArray.map { it.jsonPrimitive.int })
        assertTrue(options().getValue("WidthResolutions") is JsonArray)
    }

    @Test
    fun `a preset interval saves, and one out of range doesn't`() {
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        vm.setInterval(0)
        vm.setInterval(5000)
        saves(1)
        assertEquals(5000, options().getValue("Interval").jsonPrimitive.int)
        assertEquals(5000, vm.ready().settings.intervalMillis)
    }

    @Test
    fun `a save that can't reach the server keeps the option, says so, and Retry sends it`() {
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        jellyfin.dropped += CONFIGURATION
        vm.setHwAcceleration(true)
        val failed = vm.ready { it.save.failed }
        assertTrue(failed.settings.hwAcceleration)
        assertEquals(null, failed.refused)
        jellyfin.dropped.clear()
        vm.retrySave()
        val saved = vm.ready { it.save.status == SaveStatus.Saved }
        assertFalse(saved.save.failed)
        assertTrue(saved.settings.hwAcceleration)
    }

    @Test
    fun `a save the server refuses puts the option back and says so`() {
        val vm = made(TrickplayViewModel(connection))
        vm.ready()
        jellyfin.status[CONFIGURATION] = 500
        vm.setHwAcceleration(true)
        val failed = vm.ready { it.refused != null }
        assertFalse(failed.settings.hwAcceleration)
        assertEquals(ConsoleFailure.Refused, failed.refused)
    }

    @Test
    fun `a page that can't read says why, and Try again reads it`() {
        jellyfin.status[CONFIGURATION] = 403
        val vm = made(TrickplayViewModel(connection))
        assertEquals(TrickplayUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove(CONFIGURATION)
        vm.load()
        assertEquals(TrickplaySettings(), vm.ready().settings)
    }

    private companion object {
        const val CONFIGURATION = "/System/Configuration"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
