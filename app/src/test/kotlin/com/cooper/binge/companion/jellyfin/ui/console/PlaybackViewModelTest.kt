package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackViewModelTest {
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

    private fun PlaybackViewModel.settled(check: (PlaybackUiState) -> Boolean = { it !is PlaybackUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun PlaybackViewModel.ready() = settled() as PlaybackUiState.Ready

    /** Waits for the fake to have taken [count] saves. */
    private fun saves(count: Int) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.hits("/Users/Configuration") < count) delay(POLL_MILLIS)
            }
        }
    }

    @Test
    fun `reads the user's choices and the server's languages, one per code, the unnamed one left out`() {
        val ready = made(PlaybackViewModel(connection)).ready()
        assertEquals(PlaybackPreferences(audio = "eng", subtitles = "", subtitleMode = SubtitleMode.Default), ready.preferences)
        assertEquals(listOf("English", "French", "Klingon", "Spanish; Castilian"), ready.cultures.map { it.displayName })
        assertEquals(listOf("fre", "fra"), ready.cultures.first { it.displayName == "French" }.codes)
    }

    @Test
    fun `each pick saves the whole configuration with that one change, for this user`() {
        val vm = made(PlaybackViewModel(connection))
        vm.ready()
        vm.setSubtitles("fre")
        saves(1)
        vm.setSubtitleMode(SubtitleMode.Smart)
        saves(2)
        val body = jellyfin.lastBody("/Users/Configuration")
        assertEquals("fre", body.getValue("SubtitleLanguagePreference").jsonPrimitive.content)
        assertEquals("Smart", body.getValue("SubtitleMode").jsonPrimitive.content)
        // What the page doesn't show goes back as it was.
        assertEquals("eng", body.getValue("AudioLanguagePreference").jsonPrimitive.content)
        assertEquals("true", body.getValue("HidePlayedInLatest").jsonPrimitive.content)
        assertEquals(
            "user-1",
            jellyfin.requests
                .last { it.url.encodedPath == "/Users/Configuration" }
                .url
                .queryParameter("userId"),
        )
        assertEquals(PlaybackPreferences("eng", "fre", SubtitleMode.Smart), vm.ready().preferences)
    }

    /** A view model held in [store], so clearing the store closes it as closing the page does. */
    private fun playbackIn(store: ViewModelStore): PlaybackViewModel =
        ViewModelProvider(
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            },
            viewModelFactory { initializer { PlaybackViewModel(connection) } },
        )[PlaybackViewModel::class.java]

    @Test
    fun `closing the page mid-save still sends every pick, and a page opened next reads them`() {
        val store = ViewModelStore()
        playbackIn(store).apply {
            ready()
            setAudio("spa")
            setSubtitles("fre")
        }
        store.clear()
        saves(2)
        val body = jellyfin.lastBody("/Users/Configuration")
        assertEquals("spa", body.getValue("AudioLanguagePreference").jsonPrimitive.content)
        assertEquals("fre", body.getValue("SubtitleLanguagePreference").jsonPrimitive.content)
        assertEquals(PlaybackPreferences("spa", "fre", SubtitleMode.Default), made(PlaybackViewModel(connection)).ready().preferences)
    }

    @Test
    fun `a save queued under one session never reaches the next one signed in`() {
        val vm = made(PlaybackViewModel(connection))
        vm.ready()
        jellyfin.slow += "/Users/Configuration"
        vm.setAudio("spa")
        saves(1)
        vm.setSubtitles("fre")
        // Another account signs in while the first save is on the wire and the second waits behind it.
        runBlocking { sessions.save(jellyfin.session().copy(userId = "user-2", token = "token-2")) }
        jellyfin.slow.clear()
        runBlocking {
            connection.awaitSaves()
            delay(SETTLE_MILLIS)
        }
        assertEquals(1, jellyfin.hits("/Users/Configuration"))
    }

    @Test
    fun `a save that fails puts that choice back and says so`() {
        val vm = made(PlaybackViewModel(connection))
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 500
        vm.setAudio("spa")
        val failed = vm.settled { (it as? PlaybackUiState.Ready)?.saveFailed != null } as PlaybackUiState.Ready
        assertEquals(ConsoleFailure.Refused, failed.saveFailed)
        assertEquals("eng", failed.preferences.audio)
        // The next pick clears it.
        jellyfin.status.remove("/Users/Configuration")
        vm.setAudio("spa")
        assertEquals(null, vm.ready().saveFailed)
    }

    @Test
    fun `a failed save leaves a newer pick of the same row alone`() {
        val vm = made(PlaybackViewModel(connection))
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 500
        vm.setAudio("spa")
        saves(1)
        jellyfin.status.remove("/Users/Configuration")
        vm.setAudio("fra")
        saves(2)
        assertEquals("fra", vm.ready().preferences.audio)
        assertEquals(
            "fra",
            jellyfin
                .lastBody("/Users/Configuration")
                .getValue("AudioLanguagePreference")
                .jsonPrimitive.content,
        )
        assertEquals(null, vm.ready().saveFailed)
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/Localization/Cultures"] = 500
        val vm = made(PlaybackViewModel(connection))
        assertEquals(PlaybackUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Localization/Cultures")
        vm.load()
        assertTrue(vm.settled() is PlaybackUiState.Ready)
    }

    @Test
    fun `signed out, the page can't read`() {
        runBlocking { sessions.clear() }
        assertEquals(PlaybackUiState.Failed(ConsoleFailure.Unreachable), made(PlaybackViewModel(connection)).settled())
    }

    @Test
    fun `reads the playback switches, each on unless the server says off`() {
        jellyfin.userConfiguration = """{"PlayDefaultAudioTrack":false,"RememberSubtitleSelections":false}"""
        val ready = made(PlaybackViewModel(connection)).ready().preferences
        assertFalse(ready.playDefaultAudioTrack)
        assertTrue(ready.rememberAudio)
        assertFalse(ready.rememberSubtitles)
        assertTrue(ready.autoplayNextEpisode)
    }

    @Test
    fun `a switch saves as a boolean, with the rest of the configuration`() {
        val vm = made(PlaybackViewModel(connection))
        vm.ready()
        vm.setAutoplayNextEpisode(false)
        saves(1)
        vm.setRememberAudio(false)
        saves(2)
        val body = jellyfin.lastBody("/Users/Configuration")
        assertEquals(false, body.getValue("EnableNextEpisodeAutoPlay").jsonPrimitive.booleanOrNull)
        assertEquals(false, body.getValue("RememberAudioSelections").jsonPrimitive.booleanOrNull)
        assertEquals("eng", body.getValue("AudioLanguagePreference").jsonPrimitive.content)
        val shown = vm.ready().preferences
        assertFalse(shown.autoplayNextEpisode)
        assertFalse(shown.rememberAudio)
    }

    @Test
    fun `a switch whose save fails goes back`() {
        val vm = made(PlaybackViewModel(connection))
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 500
        vm.setPlayDefaultAudioTrack(false)
        val failed = vm.settled { (it as? PlaybackUiState.Ready)?.saveFailed != null } as PlaybackUiState.Ready
        assertTrue(failed.preferences.playDefaultAudioTrack)
        vm.setRememberSubtitles(false)
        val again = vm.settled { (it as? PlaybackUiState.Ready)?.preferences?.rememberSubtitles == true && it.saveFailed != null }
        assertTrue((again as PlaybackUiState.Ready).preferences.rememberSubtitles)
    }

    private companion object {
        const val POLL_MILLIS = 10L
        const val SETTLE_MILLIS = 300L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
