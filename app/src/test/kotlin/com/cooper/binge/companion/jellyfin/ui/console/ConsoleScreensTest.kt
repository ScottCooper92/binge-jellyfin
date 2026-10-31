package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.PlaybackLanguages
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The console's pages, driven as a user would: each tap reaches the action it should. */
@RunWith(RobolectricTestRunner::class)
class ConsoleScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val stream =
        NowPlaying("tv", "Ana", "Living room", "Web", "Sintel", null, 1, 4, paused = true, PlayMethod.Transcode, listOf("AudioIsExternal"))

    private val cultures = listOf(Culture("English", "en", listOf("eng")), Culture("French", "fr", listOf("fre", "fra")))

    @Test
    fun `now playing shows each stream, and Stop asks before it stops`() {
        val asked = mutableListOf<NowPlaying>()
        var confirmed = false
        val actions = NowPlayingActions(onStop = { asked += it }, onConfirmStop = { confirmed = true })
        var state by mutableStateOf<NowPlayingUiState>(NowPlayingUiState.Ready(listOf(stream)))
        compose.setContent { JellyfinTheme { NowPlayingScreen(state, administrator = false, actions) } }
        compose.onNodeWithText("Sintel").assertExists()
        compose.onNodeWithText("Transcoding: the device can't play the audio").assertExists()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf(stream), asked)
        state = NowPlayingUiState.Ready(listOf(stream), confirming = stream)
        compose.onNodeWithText("Stop Sintel?").assertExists()
        compose.onAllNodesWithText("Stop")[1].performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun `now playing with nothing playing says so, and a failed read offers Try again`() {
        var retried = false
        var state by mutableStateOf<NowPlayingUiState>(NowPlayingUiState.Ready(emptyList()))
        compose.setContent {
            JellyfinTheme {
                NowPlayingScreen(
                    state,
                    administrator = true,
                    NowPlayingActions(onRetry = { retried = true }),
                )
            }
        }
        compose.onNodeWithText("Nothing's playing right now.").assertExists()
        compose.onNodeWithText("Everyone streaming from this server, as it happens.").assertExists()
        state = NowPlayingUiState.Failed(ConsoleFailure.Unreachable)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(true, retried)
    }

    @Test
    fun `each direct method reads as itself`() {
        val streams =
            listOf(
                stream.copy(sessionId = "a", method = PlayMethod.DirectPlay),
                stream.copy(sessionId = "b", method = PlayMethod.DirectStream),
            )
        compose.setContent {
            JellyfinTheme {
                NowPlayingScreen(
                    NowPlayingUiState.Ready(streams),
                    administrator = true,
                    NowPlayingActions(),
                )
            }
        }
        compose.onNodeWithText("Direct play: the file as it is").assertExists()
        compose.onNodeWithText("Direct stream: repackaged, not re-encoded").assertExists()
    }

    @Test
    fun `a language row opens its sheet, and a pick reaches the action`() {
        val audio = mutableListOf<String>()
        val modes = mutableListOf<SubtitleMode>()
        val state = LanguagesUiState.Ready(PlaybackLanguages("eng", "", SubtitleMode.Default), cultures)
        compose.setContent {
            JellyfinTheme { LanguagesScreen(state, LanguagesActions(onAudio = { audio += it }, onSubtitleMode = { modes += it })) }
        }
        compose.onNodeWithText("Any language").assertExists()
        compose.onNodeWithText("Subtitles").performClick()
        compose.onNodeWithText("Always").performClick()
        assertEquals(listOf(SubtitleMode.Always), modes)
        compose.onNodeWithText("Audio language").performClick()
        compose.onNodeWithText("French").performClick()
        assertEquals(listOf("fre"), audio)
    }

    @Test
    fun `every subtitle mode is offered, with what it does`() {
        val state = LanguagesUiState.Ready(PlaybackLanguages("", "", SubtitleMode.None), cultures, saveFailed = ConsoleFailure.Refused)
        compose.setContent { JellyfinTheme { LanguagesScreen(state, LanguagesActions()) } }
        compose.onNodeWithText("Not saved. Something went wrong. Try again.").assertExists()
        compose.onNodeWithText("Subtitles").performClick()
        listOf("Follow the file", "Smart", "Only forced", "Always").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("You can still turn them on while watching").assertExists()
    }

    @Test
    fun `the password form hands each field and the submit to its view model`() {
        val typed = mutableListOf<String>()
        var changed = false
        val actions =
            PasswordActions(onCurrent = { typed += "c:$it" }, onNew = { typed += "n:$it" }, onConfirm = { typed += "r:$it" }, onChange = {
                changed =
                    true
            })
        compose.setContent { JellyfinTheme { PasswordScreen(PasswordUiState(new = "a", confirm = "a"), actions) } }
        compose.onNodeWithText("Current password").performTextInput("x")
        compose.onNodeWithText("New password again").performTextInput("y")
        compose.onNodeWithText("Change password").performScrollTo().performClick()
        // The fields are the view model's: each keystroke goes to it, against the value it holds.
        assertTrue("c:x" in typed)
        assertTrue(typed.any { it.startsWith("r:") && 'y' in it })
        assertEquals(true, changed)
    }

    @Test
    fun `each password result reads as it should`() {
        var state by mutableStateOf(PasswordUiState(result = PasswordResult.Changed))
        compose.setContent { JellyfinTheme { PasswordScreen(state, PasswordActions()) } }
        mapOf(
            PasswordResult.Changed to "Changed. Your other devices will ask for the new password.",
            PasswordResult.WrongCurrent to "That isn't your current password.",
            PasswordResult.Mismatch to "The new passwords don't match.",
            PasswordResult.Refused to "Something went wrong. Try again.",
            PasswordResult.Unreachable to "Couldn't reach your Jellyfin server",
        ).forEach { (result, text) ->
            state = PasswordUiState(result = result)
            compose.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun `libraries scan one or all as job rows, stop the scan of everything, and show how a run went`() {
        val scanned = mutableListOf<String>()
        var everything = 0
        var stopped = false
        val libraries =
            listOf(
                ServerLibrary("m", "Movies", "movies", listOf("/media/movies")),
                ServerLibrary("s", "Shows", "tvshows", listOf("/media/tv")),
            )
        val scanTask = ServerTask("t", LIBRARY_SCAN_TASK, "Scan Media Library", "", "Library", false, false, null, null)
        var state by mutableStateOf(LibrariesUiState.Ready(libraries, TaskBoard(listOf(scanTask))))
        val actions =
            LibrariesActions(onScan = { scanned += it.id }, onScanEverything = { everything++ }, onStopEverything = { stopped = true })
        compose.setContent { JellyfinTheme { LibrariesScreen(state, actions) } }
        compose.onNodeWithText("Hasn't run yet").assertExists()
        compose.onAllNodesWithContentDescription("Scan")[0].performClick()
        assertEquals(1, everything)
        compose.onAllNodesWithContentDescription("Scan")[1].performClick()
        assertEquals(listOf("m"), scanned)
        state =
            state.copy(
                board = TaskBoard(listOf(scanTask.copy(running = true, percent = 12))),
                scanning = mapOf("m" to LibraryScan(40, started = true)),
                outcomes = mapOf("s" to JobOutcome.Failed),
                actionFailed = ConsoleFailure.Unreachable,
            )
        compose.onNodeWithText("Running… 12%").assertExists()
        compose.onNodeWithText("Running… 40%").assertExists()
        compose.onNodeWithText("Failed").assertExists()
        compose.onNodeWithContentDescription("Stop").performClick()
        assertTrue(stopped)
        val notStarted = "The scan didn't start. Couldn't reach your Jellyfin server"
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(notStarted))
        compose.onNodeWithText(notStarted).assertExists()
        state = state.copy(board = TaskBoard(listOf(scanTask)), scanning = emptyMap(), outcomes = mapOf("m" to JobOutcome.Succeeded))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Done"))
        compose.onNodeWithText("Done").assertExists()
    }

    @Test
    fun `each kind of library has its icon, and a mixed one a folder`() {
        val icons = listOf("movies", "tvshows", "music", "books", "photos", "homevideos", "", "boxsets").map(::typeIcon)
        assertEquals(6, icons.distinct().size)
        assertEquals(typeIcon(""), typeIcon("boxsets"))
    }
}
