package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.playback.StreamingNetwork
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.console.ConsoleFailure
import com.cooper.homelab.ephyra.ui.console.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The device's two streaming limits under This app: always shown once read, whatever the server's preferences did. */
@RunWith(RobolectricTestRunner::class)
class ThisAppQualityTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun show(
        quality: StreamingQualitySettings?,
        playback: PlaybackUiState? = null,
        onQuality: (StreamingNetwork, Int) -> Unit = { _, _ -> },
    ) = compose.setContent {
        JellyfinTheme { ThisAppScreen(Actions(), onBack = {}, playback = playback, quality = quality, onQuality = onQuality) }
    }

    @Test
    fun `no quality state means no quality rows`() {
        show(null)
        compose.onAllNodesWithText("Maximum quality on Wi-Fi", substring = true).assertCountEquals(0)
    }

    @Test
    fun `each row shows its value, auto where no limit is set`() {
        show(StreamingQualitySettings(wifiMbps = 20, mobileMbps = 0))
        compose.onNodeWithText("PLAYBACK").assertExists()
        compose.onNodeWithText("Maximum quality on Wi-Fi", substring = true).assertExists()
        compose.onNodeWithText("20 Mbps", substring = true).assertExists()
        compose.onNodeWithText("Maximum quality on mobile data", substring = true).assertExists()
        compose.onNodeWithText("Auto", substring = true).assertExists()
    }

    @Test
    fun `the rows are there when the server's preferences could not be read`() {
        show(StreamingQualitySettings(0, 0), playback = PlaybackUiState.Failed(ConsoleFailure.Unreachable))
        compose.onNodeWithText("Maximum quality on Wi-Fi", substring = true).assertExists()
        compose.onAllNodesWithText("Audio language", substring = true).assertCountEquals(0)
    }

    @Test
    fun `picking a step on Wi-Fi sends that connection and the megabits`() {
        val picked = mutableListOf<Pair<StreamingNetwork, Int>>()
        show(StreamingQualitySettings(0, 0)) { network, mbps -> picked += network to mbps }
        compose.onNodeWithText("Maximum quality on Wi-Fi", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("40 Mbps").performClick()
        assertEquals(listOf(StreamingNetwork.Wifi to 40), picked)
    }

    @Test
    fun `picking auto on mobile data sends zero`() {
        val picked = mutableListOf<Pair<StreamingNetwork, Int>>()
        show(StreamingQualitySettings(0, 4)) { network, mbps -> picked += network to mbps }
        compose.onNodeWithText("Maximum quality on mobile data", substring = true).performScrollTo().performClick()
        compose.onAllNodesWithText("Auto").onLast().performClick()
        assertEquals(listOf(StreamingNetwork.Mobile to 0), picked)
    }
}
