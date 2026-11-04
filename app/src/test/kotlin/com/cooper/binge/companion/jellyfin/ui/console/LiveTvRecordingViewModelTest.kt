package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_GUIDE_DAYS
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_MOVIE_PATH
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_POST_PADDING
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_PRE_PADDING
import com.cooper.binge.companion.jellyfin.server.LIVE_TV_SUBFOLDERS
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
class LiveTvRecordingViewModelTest {
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

    private fun LiveTvRecordingViewModel.ready(check: (LiveTvRecordingUiState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { state.first { it is LiveTvRecordingUiState.Ready && check(it) } }
        } as LiveTvRecordingUiState.Ready

    private fun saved(count: Int) =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.requests.count { it.url.encodedPath == PATH && it.method == "POST" } < count) delay(POLL_MILLIS)
            }
        }

    @Test
    fun `reads the configuration, tuners and providers included`() {
        val ready = made(LiveTvRecordingViewModel(connection)).ready()
        assertEquals(
            "/recordings",
            ready.config
                .getValue("RecordingPath")
                .jsonPrimitive.content,
        )
        assertEquals(
            2,
            ready.config
                .getValue("TunerHosts")
                .jsonArray.size,
        )
    }

    @Test
    fun `a switch saves the whole configuration with that one change, and what the page does not show goes back as it came`() {
        val vm = made(LiveTvRecordingViewModel(connection))
        vm.ready()
        vm.set(LIVE_TV_SUBFOLDERS, JsonPrimitive(true))
        saved(1)
        val sent = jellyfin.lastBody(PATH)
        assertTrue(sent.getValue(LIVE_TV_SUBFOLDERS).jsonPrimitive.boolean)
        assertEquals(
            "me",
            sent
                .getValue("FutureOption")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
        assertEquals(
            listOf("t1", "t2"),
            sent.getValue("TunerHosts").jsonArray.map {
                it.jsonObject
                    .getValue("Id")
                    .jsonPrimitive.content
            },
        )
        val stored =
            sent
                .getValue("ListingProviders")
                .jsonArray[1]
                .jsonObject
                .getValue("Password")
                .jsonPrimitive.content
        assertEquals("5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8", stored)
    }

    @Test
    fun `padding keeps whole seconds without converting to minutes`() {
        val vm = made(LiveTvRecordingViewModel(connection))
        vm.ready()
        vm.saveNumber(RecordingNumber.PrePadding, "2")
        vm.saveNumber(RecordingNumber.PostPadding, "10")
        saved(1)
        val sent = jellyfin.lastBody(PATH)
        assertEquals(2, sent.getValue(LIVE_TV_PRE_PADDING).jsonPrimitive.int)
        assertEquals(10, sent.getValue(LIVE_TV_POST_PADDING).jsonPrimitive.int)
        assertEquals("2", RecordingNumber.PrePadding.shown(vm.ready().config))
    }

    @Test
    fun `a number out of range saves nothing and keeps its sheet open`() {
        val vm = made(LiveTvRecordingViewModel(connection))
        vm.ready()
        vm.sheet(RecordingSheet.Number(RecordingNumber.PostPadding))
        vm.saveNumber(RecordingNumber.PostPadding, "121")
        vm.saveNumber(RecordingNumber.PostPadding, "-1")
        vm.saveNumber(RecordingNumber.PostPadding, "1.5")
        vm.saveNumber(RecordingNumber.PostPadding, "")
        assertEquals(RecordingSheet.Number(RecordingNumber.PostPadding), vm.ready().sheet)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == PATH && it.method == "POST" })
    }

    @Test
    fun `an empty guide length clears it for the server to choose, and a length out of range is refused`() {
        val vm = made(LiveTvRecordingViewModel(connection))
        vm.ready()
        assertNull(RecordingNumber.GuideDays.parse("0"))
        assertNull(RecordingNumber.GuideDays.parse("15"))
        vm.saveNumber(RecordingNumber.GuideDays, " ")
        saved(1)
        assertEquals(JsonNull, jellyfin.lastBody(PATH).getValue(LIVE_TV_GUIDE_DAYS))
        assertEquals("", RecordingNumber.GuideDays.shown(vm.ready().config))
    }

    @Test
    fun `a folder saves as chosen and closes its sheet, and a blank path leaves it unset`() {
        val vm = made(LiveTvRecordingViewModel(connection))
        vm.ready()
        vm.sheet(RecordingSheet.Folder(RecordingFolder.Movies))
        vm.saveFolder(RecordingFolder.Movies, "  /media/films ")
        assertNull(vm.ready().sheet)
        saved(1)
        assertEquals(
            "/media/films",
            jellyfin
                .lastBody(PATH)
                .getValue(LIVE_TV_MOVIE_PATH)
                .jsonPrimitive.content,
        )
        vm.saveFolder(RecordingFolder.Movies, "")
        saved(2)
        assertEquals(
            "",
            jellyfin
                .lastBody(PATH)
                .getValue(LIVE_TV_MOVIE_PATH)
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a configuration the server will not give is a failure the page can retry`() {
        jellyfin.liveTvConfiguration = ""
        val vm = made(LiveTvRecordingViewModel(connection))
        runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is LiveTvRecordingUiState.Failed } } }
        jellyfin.liveTvConfiguration = """{"RecordingPath":"/r","TunerHosts":[],"ListingProviders":[]}"""
        vm.load()
        assertEquals(
            "/r",
            vm
                .ready()
                .config
                .getValue("RecordingPath")
                .jsonPrimitive.content,
        )
    }

    private companion object {
        const val PATH = "/System/Configuration/livetv"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
