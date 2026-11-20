package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.server.Culture
import com.cooper.homelab.ephyra.server.PlaybackPreferences
import com.cooper.homelab.ephyra.server.SubtitleMode
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.console.ConsoleFailure
import com.cooper.homelab.ephyra.ui.console.PlaybackActions
import com.cooper.homelab.ephyra.ui.console.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The server's language preferences under This app: the playback page's own rows, shown only once they are read. */
@RunWith(RobolectricTestRunner::class)
class ThisAppPlaybackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val cultures =
        listOf(
            Culture("English", "en", listOf("eng")),
            Culture("Japanese", "ja", listOf("jpn")),
            Culture("French", "fr", listOf("fre", "fra")),
        )
    private val ready = PlaybackUiState.Ready(PlaybackPreferences("jpn", "eng", SubtitleMode.Smart), cultures)

    private fun show(
        playback: PlaybackUiState?,
        actions: PlaybackActions = PlaybackActions(),
    ) = compose.setContent { JellyfinTheme { ThisAppScreen(Actions(), onBack = {}, playback = playback, playbackActions = actions) } }

    @Test
    fun `a page with no playback state has no playback group`() {
        show(null)
        compose.onAllNodesWithText("Audio language").assertCountEquals(0)
    }

    @Test
    fun `nothing shows while the preferences are read, or when they cannot be`() {
        show(PlaybackUiState.Loading)
        compose.onAllNodesWithText("Audio language").assertCountEquals(0)
        compose.onAllNodesWithText("PLAYBACK").assertCountEquals(0)
    }

    @Test
    fun `a failed read leaves the group out`() {
        show(PlaybackUiState.Failed(ConsoleFailure.Unreachable))
        compose.onAllNodesWithText("Audio language").assertCountEquals(0)
    }

    @Test
    fun `the three rows show the user's choices as their details`() {
        show(ready)
        compose.onNodeWithText("PLAYBACK").assertExists()
        compose.onNodeWithText("Audio language").assertExists()
        compose.onNodeWithText("Japanese").assertExists()
        compose.onNodeWithText("Subtitle language").assertExists()
        compose.onNodeWithText("English").assertExists()
        compose.onNodeWithText("Subtitle mode").assertExists()
    }

    @Test
    fun `picking a language sends it to the same action the playback page uses`() {
        val chosen = mutableListOf<String>()
        show(ready, PlaybackActions(onAudio = { chosen += it }))
        compose.onNodeWithText("Audio language").performScrollTo().performClick()
        compose.onNodeWithText("French").performClick()
        assertEquals(listOf("fre"), chosen)
    }
}
