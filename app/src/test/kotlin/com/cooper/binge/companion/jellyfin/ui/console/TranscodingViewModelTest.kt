package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ENCODING_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.ENCODING_DECODING_CODECS
import com.cooper.binge.companion.jellyfin.server.ENCODING_HARDWARE_ENCODING
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
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
class TranscodingViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.serverConfiguration =
            """{"ServerName":"truenas","MinResumePct":5,"MaxResumePct":90,"MinResumeDurationSeconds":300,""" +
            """"MinAudiobookResume":5,"MaxAudiobookResume":5,"RemoteClientBitrateLimit":0,"FutureSetting":{"keep":"me"}}"""
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun TranscodingViewModel.ready(check: (TranscodingUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(
                TIMEOUT_MILLIS,
            ) { state.first { it is TranscodingUiState.Ready && check(it) } }
        } as TranscodingUiState.Ready

    private fun saves(
        path: String,
        count: Int,
    ) = runBlocking {
        withTimeout(TIMEOUT_MILLIS) {
            while (jellyfin.requests.count { it.url.encodedPath == path && it.method == "POST" } <
                count
            ) {
                delay(POLL_MILLIS)
            }
        }
    }

    @Test
    fun `reads the encoding and the server configuration`() {
        val ready = made(TranscodingViewModel(connection)).ready()
        assertEquals(
            "qsv",
            ready.encoding
                .getValue(ENCODING_ACCELERATION)
                .jsonPrimitive.content,
        )
        assertEquals(
            "truenas",
            ready.server
                .getValue("ServerName")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `an encoding setting saves the whole encoding configuration with that one change`() {
        val vm = made(TranscodingViewModel(connection))
        vm.ready()
        vm.setEncoding(ENCODING_ACCELERATION, JsonPrimitive("vaapi"))
        vm.setEncoding(ENCODING_HARDWARE_ENCODING, JsonPrimitive(false))
        saves(ENCODING, 2)
        val sent = jellyfin.lastBody(ENCODING)
        assertEquals("vaapi", sent.getValue(ENCODING_ACCELERATION).jsonPrimitive.content)
        assertEquals(false, sent.getValue(ENCODING_HARDWARE_ENCODING).jsonPrimitive.boolean)
        // What the page doesn't change goes back as it was, an unknown key included.
        assertEquals("/dev/dri/renderD128", sent.getValue("VaapiDevice").jsonPrimitive.content)
        assertEquals(
            "me",
            sent
                .getValue("FutureOption")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == "/System/Configuration" && it.method == "POST" })
    }

    @Test
    fun `a server setting saves the whole server configuration, not the encoding one`() {
        val vm = made(TranscodingViewModel(connection))
        vm.ready()
        vm.setServer("MinResumePct", JsonPrimitive(10))
        saves(SERVER, 1)
        val sent = jellyfin.lastBody(SERVER)
        assertEquals(10, sent.getValue("MinResumePct").jsonPrimitive.int)
        assertEquals("truenas", sent.getValue("ServerName").jsonPrimitive.content)
        assertEquals(
            "me",
            sent
                .getValue("FutureSetting")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == ENCODING && it.method == "POST" })
    }

    @Test
    fun `a hardware decoding codec switches on and off in the list`() {
        val vm = made(TranscodingViewModel(connection))
        vm.ready()
        vm.setCodec("av1", true)
        vm.setCodec("h264", false)
        vm.setCodec("av1", true)
        saves(ENCODING, 3)
        assertEquals(
            listOf("hevc", "av1"),
            jellyfin.lastBody(ENCODING).getValue(ENCODING_DECODING_CODECS).jsonArray.map {
                it.jsonPrimitive.content
            },
        )
    }

    @Test
    fun `a number saves in its configuration with its sheet, and out of range keeps the sheet open`() {
        val vm = made(TranscodingViewModel(connection))
        vm.ready()
        vm.sheet(TranscodingSheet.Number(NumberField.DownmixBoost))
        vm.saveNumber(NumberField.DownmixBoost, "9")
        assertEquals(TranscodingSheet.Number(NumberField.DownmixBoost), vm.ready().sheet)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == ENCODING && it.method == "POST" })
        vm.saveNumber(NumberField.DownmixBoost, "1,5")
        assertNull(vm.ready { it.sheet == null }.sheet)
        saves(ENCODING, 1)
        assertEquals(
            1.5,
            jellyfin
                .lastBody(ENCODING)
                .getValue("DownMixAudioBoost")
                .jsonPrimitive.double,
            0.0,
        )
        vm.saveNumber(NumberField.MaxResumePercent, "85")
        saves(SERVER, 1)
        assertEquals(
            85,
            jellyfin
                .lastBody(SERVER)
                .getValue("MaxResumePct")
                .jsonPrimitive.int,
        )
    }

    @Test
    fun `the bitrate is typed in megabits and stored in bits`() {
        val vm = made(TranscodingViewModel(connection))
        vm.ready()
        vm.saveNumber(NumberField.BitrateLimit, "8")
        saves(SERVER, 1)
        assertEquals(
            8_000_000,
            jellyfin
                .lastBody(SERVER)
                .getValue("RemoteClientBitrateLimit")
                .jsonPrimitive.int,
        )
        vm.saveNumber(NumberField.BitrateLimit, "2.5")
        saves(SERVER, 2)
        assertEquals(
            2_500_000,
            jellyfin
                .lastBody(SERVER)
                .getValue("RemoteClientBitrateLimit")
                .jsonPrimitive.int,
        )
    }

    @Test
    fun `each number has the range jellyfin-web sets, and a whole one takes no fraction`() {
        assertEquals(JsonPrimitive(10), NumberField.ThrottleDelay.parse("10"))
        assertNull(NumberField.ThrottleDelay.parse("9"))
        assertNull(NumberField.ThrottleDelay.parse("3601"))
        assertNull(NumberField.ThrottleDelay.parse("12.5"))
        assertNull(NumberField.SegmentKeep.parse("14"))
        assertNull(NumberField.VppContrast.parse("0.9"))
        assertEquals(JsonPrimitive(1.5), NumberField.VppContrast.parse("1.5"))
        assertNull(NumberField.DownmixBoost.parse("0.4"))
        assertNull(NumberField.MinResumePercent.parse("101"))
        assertNull(NumberField.MinResumePercent.parse("abc"))
        assertTrue(NumberField.entries.all { it.parse(it.min.toString().removeSuffix(".0")) != null })
    }

    @Test
    fun `a refused save goes back and says so, and a page that can't read says why`() {
        val vm = made(TranscodingViewModel(connection))
        vm.ready()
        jellyfin.status[ENCODING] = 500
        vm.setEncoding(ENCODING_ACCELERATION, JsonPrimitive("vaapi"))
        val back = vm.ready { it.saveFailed != null }
        assertEquals(
            "qsv",
            back.encoding
                .getValue(ENCODING_ACCELERATION)
                .jsonPrimitive.content,
        )
        val failing = made(TranscodingViewModel(connection))
        runBlocking { withTimeout(TIMEOUT_MILLIS) { failing.state.first { it is TranscodingUiState.Failed } } }
        jellyfin.status.remove(ENCODING)
        failing.load()
        failing.ready()
    }

    private companion object {
        const val ENCODING = "/System/Configuration/encoding"
        const val SERVER = "/System/Configuration"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
