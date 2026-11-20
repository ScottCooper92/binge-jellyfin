package com.cooper.homelab.ephyra.ui.client

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.client.title.PlayKind
import com.cooper.homelab.ephyra.client.title.SeriesPlay
import com.cooper.homelab.ephyra.demo.DEMO_ORIGIN
import com.cooper.homelab.ephyra.playback.PlayItem
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.player.api.PlayTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Every Play goes through one starter: where the server cannot be played from it says why and opens no player. */
@RunWith(RobolectricTestRunner::class)
class PlayStartHostTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val graph by lazy { AppGraph(ApplicationProvider.getApplicationContext(), sessionsForTest = testSessionStore()) }
    private var opened = false

    private fun host(serverAddress: String) =
        compose.setContent {
            JellyfinTheme {
                PlayStartHost(graph, serverAddress, onPlayer = { _, _ -> opened = true }) { play ->
                    Text("Play it", Modifier.clickable { play(PlayItem("i1", PlayTitle("Colour Bars"))) })
                }
            }
        }

    @Test
    fun `a play in the demo says there is nothing to play, and opens no player`() {
        host(DEMO_ORIGIN)
        compose.onNodeWithText("Play it").performClick()
        compose.onNodeWithText("Nothing to play in the demo").assertIsDisplayed()
        assertFalse(opened)
    }

    @Test
    fun `a play on plain http at a public address says why, and OK closes the message`() {
        host("http://jellyfin.example.com")
        compose.onNodeWithText("Play it").performClick()
        compose.onNodeWithText("Can't play from this server").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Can't play from this server").assertDoesNotExist()
        assertFalse(opened)
    }

    @Test
    fun `a series' button plays the episode to resume or continue with, the first when all are watched, and none when there is none`() {
        val resume = SeriesPlay.Episode(PlayKind.Resume, "e2", 1, 2, 8)
        val first = SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null)
        assertEquals(resume, resume.episode())
        assertEquals(first, SeriesPlay.AllWatched(first).episode())
        assertNull(SeriesPlay.NoEpisodes.episode())
        assertNull((null as SeriesPlay?).episode())
    }
}
