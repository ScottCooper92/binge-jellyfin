package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** How a settings page tells the user where its saves stand: a Retry for a change not sent, a message for one refused. */
@RunWith(RobolectricTestRunner::class)
class SaveAsMadeStatusTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ready = PlaybackUiState.Ready(PlaybackPreferences("", "", SubtitleMode.None), emptyList())

    @Test
    fun `a change that could not be sent offers Retry, which sends it again`() {
        var retries = 0
        compose.setContent {
            JellyfinTheme { PlaybackScreen(ready.copy(save = SaveProgress(failed = true)), PlaybackActions(onRetrySave = { retries++ })) }
        }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `a refused change says why it went back`() {
        compose.setContent { JellyfinTheme { PlaybackScreen(ready.copy(refused = ConsoleFailure.Refused), PlaybackActions()) } }
        compose.onNodeWithText("Not saved. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a refusal after an earlier failure with the same words is still said`() {
        val host = SnackbarHostState()
        var refused by mutableStateOf<ConsoleFailure?>(null)
        compose.setContent {
            JellyfinTheme {
                RefusedMessage(refused, host)
                RefusedMessage(ConsoleFailure.Refused, host)
            }
        }
        val message = "Not saved. Something went wrong. Try again."
        compose.waitForIdle()
        assertEquals(message, host.currentSnackbarData?.visuals?.message)
        host.currentSnackbarData?.dismiss()
        compose.waitForIdle()
        assertNull(host.currentSnackbarData)
        compose.runOnIdle { refused = ConsoleFailure.Refused }
        compose.waitForIdle()
        assertEquals(message, host.currentSnackbarData?.visuals?.message)
    }
}
