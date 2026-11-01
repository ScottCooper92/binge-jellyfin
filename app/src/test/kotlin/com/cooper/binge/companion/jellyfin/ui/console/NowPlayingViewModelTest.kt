package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
class NowPlayingViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    /** The view models a test made; their sockets are stopped before Main is reset, so a late push can't race the reset. */
    private val viewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> made(viewModel: T): T = viewModel.also { viewModels += it }

    @After
    fun close() {
        viewModels.forEach { it.viewModelScope.cancel() }
        jellyfin.close()
        Dispatchers.resetMain()
    }

    private fun NowPlayingViewModel.settled(check: (NowPlayingUiState) -> Boolean = { it !is NowPlayingUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun NowPlayingViewModel.ready(check: (NowPlayingUiState.Ready) -> Boolean = { true }) =
        settled { it is NowPlayingUiState.Ready && check(it) } as NowPlayingUiState.Ready

    /** Waits until the page's socket has subscribed to the session pushes. */
    private fun subscribed() =
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) { while (jellyfin.socketMessages.none { "SessionsStart" in it }) delay(POLL_MILLIS) }
        }

    @Test
    fun `reads what's playing, leaving out sessions playing nothing`() {
        val ready = made(NowPlayingViewModel(connection)).ready()
        val stream = ready.sessions.single()
        assertEquals("Sintel", stream.title)
        assertEquals(PlayMethod.Transcode, stream.method)
        // The video is the costlier reason, so it's the one the page names.
        assertEquals(R.string.transcode_video, transcodeReason(stream.transcodeReasons))
    }

    @Test
    fun `the list follows the server's pushes on the page's own socket`() {
        val vm = made(NowPlayingViewModel(connection))
        vm.ready()
        subscribed()
        jellyfin.push("""{"MessageType":"Sessions","Data":[]}""")
        assertTrue(vm.ready { it.sessions.isEmpty() }.sessions.isEmpty())
        jellyfin.push("""{"MessageType":"Sessions","Data":${FakeJellyfin.SESSIONS}}""")
        assertEquals(
            "Sintel",
            vm
                .ready { it.sessions.isNotEmpty() }
                .sessions
                .single()
                .title,
        )
    }

    @Test
    fun `stop asks first, then asks the server to stop that session`() {
        val vm = made(NowPlayingViewModel(connection))
        val stream = vm.ready().sessions.single()
        vm.askStop(stream)
        assertEquals(stream, vm.ready().confirming)
        vm.cancelStop()
        assertNull(vm.ready().confirming)
        assertEquals(0, jellyfin.hits("/Sessions/s-tv/Playing/Stop"))
        vm.askStop(stream)
        vm.stop()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { while (jellyfin.hits("/Sessions/s-tv/Playing/Stop") == 0) delay(POLL_MILLIS) } }
        assertNull(vm.ready { it.stopping == null }.stopFailed)
    }

    @Test
    fun `a stop that fails says so, and a confirm for a stream that ended closes`() {
        val vm = made(NowPlayingViewModel(connection))
        val stream = vm.ready().sessions.single()
        jellyfin.status["/Sessions/s-tv/Playing/Stop"] = 500
        vm.askStop(stream)
        vm.stop()
        assertEquals(ConsoleFailure.Refused, vm.ready { it.stopFailed != null }.stopFailed)
        subscribed()
        vm.askStop(stream)
        jellyfin.push("""{"MessageType":"Sessions","Data":[]}""")
        assertNull(vm.ready { it.sessions.isEmpty() }.confirming)
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/Sessions"] = 500
        val vm = made(NowPlayingViewModel(connection))
        assertEquals(NowPlayingUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Sessions")
        vm.load()
        assertEquals(1, vm.ready().sessions.size)
    }

    @Test
    fun `each transcode reason is named, the costliest first`() {
        assertEquals(R.string.transcode_subtitles, transcodeReason(listOf("VideoCodecNotSupported", "SubtitleCodecNotSupported")))
        assertEquals(R.string.transcode_audio, transcodeReason(listOf("AudioChannelsNotSupported")))
        assertEquals(R.string.transcode_bitrate, transcodeReason(listOf("ContainerBitrateExceedsLimit")))
        assertEquals(R.string.transcode_container, transcodeReason(listOf("ContainerNotSupported")))
        assertEquals(R.string.transcode_other, transcodeReason(listOf("DirectPlayError")))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
