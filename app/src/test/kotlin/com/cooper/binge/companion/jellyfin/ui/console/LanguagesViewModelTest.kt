package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.PlaybackLanguages
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
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
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LanguagesViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Binge Jellyfin", "Test", "1.0", Dispatchers.IO)

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

    private fun LanguagesViewModel.settled(check: (LanguagesUiState) -> Boolean = { it !is LanguagesUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun LanguagesViewModel.ready() = settled() as LanguagesUiState.Ready

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
        val ready = LanguagesViewModel(connection).ready()
        assertEquals(PlaybackLanguages(audio = "eng", subtitles = "", subtitleMode = SubtitleMode.Default), ready.languages)
        assertEquals(listOf("English", "French", "Klingon", "Spanish; Castilian"), ready.cultures.map { it.displayName })
        assertEquals(listOf("fre", "fra"), ready.cultures.first { it.displayName == "French" }.codes)
    }

    @Test
    fun `each pick saves the whole configuration with that one change, for this user`() {
        val vm = LanguagesViewModel(connection)
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
        assertEquals(PlaybackLanguages("eng", "fre", SubtitleMode.Smart), vm.ready().languages)
    }

    @Test
    fun `a save that fails puts that choice back and says so`() {
        val vm = LanguagesViewModel(connection)
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 500
        vm.setAudio("spa")
        val failed = vm.settled { (it as? LanguagesUiState.Ready)?.saveFailed != null } as LanguagesUiState.Ready
        assertEquals(ConsoleFailure.Refused, failed.saveFailed)
        assertEquals("eng", failed.languages.audio)
        // The next pick clears it.
        jellyfin.status.remove("/Users/Configuration")
        vm.setAudio("spa")
        assertEquals(null, vm.ready().saveFailed)
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/Localization/Cultures"] = 500
        val vm = LanguagesViewModel(connection)
        assertEquals(LanguagesUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Localization/Cultures")
        vm.load()
        assertTrue(vm.settled() is LanguagesUiState.Ready)
    }

    @Test
    fun `signed out, the page can't read`() {
        runBlocking { sessions.clear() }
        assertEquals(LanguagesUiState.Failed(ConsoleFailure.Unreachable), LanguagesViewModel(connection).settled())
    }

    private companion object {
        const val POLL_MILLIS = 10L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
