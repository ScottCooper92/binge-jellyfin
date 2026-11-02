package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.PlayMethod
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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
class NowPlayingViewModelTest {
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

    private fun controlSessions(vararg ids: String) =
        ids.joinToString(",", "[", "]") { id ->
            val owner = if (id == "s-other") OTHER_USER else "user-1"
            """{"Id":"$id","UserId":"$owner","DeviceId":"dev-$id","UserName":"u","DeviceName":"d","Client":"c",""" +
                """"SupportsRemoteControl":true,"SupportsMediaControl":true,""" +
                """"SupportedCommands":["Mute","Unmute","SetVolume","DisplayMessage"],""" +
                """"PlayState":{"PositionTicks":6000000000,"IsPaused":false,"CanSeek":true,"PlayMethod":"DirectPlay"},""" +
                """"NowPlayingItem":{"Name":"Sintel","RunTimeTicks":8880000000}}"""
        }

    private fun commandPosts() = jellyfin.requests.filter { it.method == "POST" && "/Playing/" in it.url.encodedPath }

    @Test
    fun `a control goes to the tapped session and no other, with the seek position`() {
        jellyfin.sessions = controlSessions("s-mine", "s-other")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = true))))
        val ready = vm.ready()
        val other = ready.sessions.first { it.sessionId == "s-other" }
        vm.select(other)
        vm.remote.seekBy(other, 10_000)
        vm.ready { !it.sending }
        val post = commandPosts().single()
        assertEquals("/Sessions/s-other/Playing/Seek", post.url.encodedPath)
        assertEquals((6_000_000_000L + 100_000_000L).toString(), post.url.queryParameter("seekPositionTicks"))
    }

    @Test
    fun `a control the account does not allow sends nothing`() {
        jellyfin.sessions = controlSessions("s-mine", "s-other")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val other = vm.ready().sessions.first { it.sessionId == "s-other" }
        vm.remote.playPause(other)
        vm.remote.skip(other, true)
        vm.askStop(other)
        vm.stop()
        assertTrue(commandPosts().isEmpty())
        assertNull(vm.ready().confirming)
    }

    @Test
    fun `a control the server's controllable list leaves out sends nothing`() {
        jellyfin.sessions = controlSessions("s-mine", "s-other")
        jellyfin.controllableSessionIds = setOf("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = true))))
        val other = vm.ready().sessions.first { it.sessionId == "s-other" }
        vm.remote.playPause(other)
        assertTrue(commandPosts().isEmpty())
        assertTrue(
            vm
                .ready()
                .controlsFor(other)
                .any
                .not(),
        )
    }

    @Test
    fun `play and pause follow the session's state, and a seek stays inside the media`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val mine = vm.ready().sessions.single()
        vm.remote.playPause(mine)
        vm.ready { !it.sending }
        vm.remote.seekTo(mine, 99_000_000_000L)
        vm.ready { !it.sending }
        val paths = commandPosts().map { it.url.encodedPath }
        assertEquals(listOf("/Sessions/s-mine/Playing/Pause", "/Sessions/s-mine/Playing/Seek"), paths)
        assertEquals("8880000000", commandPosts().last().url.queryParameter("seekPositionTicks"))
    }

    @Test
    fun `a session that leaves the feed closes its page`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        vm.select(vm.ready().sessions.single())
        subscribed()
        jellyfin.push("""{"MessageType":"Sessions","Data":[]}""")
        assertNull(vm.ready { it.sessions.isEmpty() }.selected)
    }

    @Test
    fun `a command the server cannot be reached for says so and keeps the page`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val mine = vm.ready().sessions.single()
        vm.select(mine)
        jellyfin.dropped += "/Sessions/s-mine/Playing/Pause"
        vm.remote.playPause(mine)
        val failed = vm.ready { it.sendFailed != null }
        assertEquals("s-mine", failed.selected)
        assertFalse(failed.sending)
    }

    private fun commandBodies() = jellyfin.requests.filter { it.method == "POST" && it.url.encodedPath.endsWith("/Command") }

    private fun messageBodies() = jellyfin.requests.filter { it.method == "POST" && it.url.encodedPath.endsWith("/Message") }

    @Test
    fun `volume goes to the tapped session as SetVolume, and mute and unmute follow its state`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val mine = vm.ready().sessions.single()
        vm.remote.setVolume(mine, 40)
        vm.ready { !it.sending }
        val body =
            Json
                .parseToJsonElement(
                    commandBodies()
                        .single()
                        .body
                        ?.utf8()
                        .orEmpty(),
                ).jsonObject
        assertEquals("/Sessions/s-mine/Command", commandBodies().single().url.encodedPath)
        assertEquals("SetVolume", body.getValue("Name").jsonPrimitive.contentOrNull)
        assertEquals(
            "40",
            body
                .getValue("Arguments")
                .jsonObject
                .getValue("Volume")
                .jsonPrimitive.contentOrNull,
        )
        vm.remote.toggleMute(mine)
        vm.ready { !it.sending }
        assertEquals(1, jellyfin.hits("/Sessions/s-mine/Command/Mute"))
    }

    @Test
    fun `a message is sent to the tapped session with its text and a fixed timeout, and closes the sheet`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val mine = vm.ready().sessions.single()
        vm.remote.askMessage(mine)
        assertEquals("s-mine", vm.ready().messaging)
        vm.remote.sendMessage(mine, "  ", " Dinner is ready ")
        vm.ready { !it.messageSending && it.messaging == null }
        val body =
            Json
                .parseToJsonElement(
                    messageBodies()
                        .single()
                        .body
                        ?.utf8()
                        .orEmpty(),
                ).jsonObject
        assertEquals("/Sessions/s-mine/Message", messageBodies().single().url.encodedPath)
        assertEquals("Dinner is ready", body.getValue("Text").jsonPrimitive.contentOrNull)
        assertEquals(false, "Header" in body)
        assertEquals(MESSAGE_TIMEOUT_MILLIS, body.getValue("TimeoutMs").jsonPrimitive.long)
    }

    @Test
    fun `a message that is blank or too long sends nothing and keeps the sheet`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val mine = vm.ready().sessions.single()
        vm.remote.askMessage(mine)
        vm.remote.sendMessage(mine, "", "   ")
        vm.remote.sendMessage(mine, "", "x".repeat(MESSAGE_TEXT_MAX + 1))
        vm.remote.sendMessage(mine, "h".repeat(MESSAGE_HEADER_MAX + 1), "hi")
        assertTrue(messageBodies().isEmpty())
        assertEquals("s-mine", vm.ready().messaging)
    }

    @Test
    fun `a message the account may not send sends nothing, and the sheet does not open`() {
        jellyfin.sessions = controlSessions("s-mine", "s-other")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val other = vm.ready().sessions.first { it.sessionId == "s-other" }
        vm.remote.askMessage(other)
        vm.remote.sendMessage(other, "", "hello")
        assertNull(vm.ready().messaging)
        assertTrue(messageBodies().isEmpty())
    }

    @Test
    fun `a message that cannot reach the server keeps the sheet and says so`() {
        jellyfin.sessions = controlSessions("s-mine")
        val vm = made(NowPlayingViewModel(connection, JellyfinAccount("me", policyOf(remote = false))))
        val mine = vm.ready().sessions.single()
        vm.remote.askMessage(mine)
        jellyfin.dropped += "/Sessions/s-mine/Message"
        vm.remote.sendMessage(mine, "", "hello")
        val failed = vm.ready { it.messageFailed != null }
        assertEquals("s-mine", failed.messaging)
        assertFalse(failed.messageSending)
    }

    private fun policyOf(remote: Boolean) =
        kotlinx.serialization.json.Json
            .parseToJsonElement("""{"IsAdministrator":false,"EnableRemoteControlOfOtherUsers":$remote}""")
            .let { it as kotlinx.serialization.json.JsonObject }

    private companion object {
        const val OTHER_USER = "OTHERUSER"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
