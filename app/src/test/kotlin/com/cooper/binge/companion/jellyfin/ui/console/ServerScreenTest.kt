package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.jellyfin.server.ServerInfo
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A restart the server never came back from is a message dialog, a cause and a next step (#260), and one it refused stays a short
 * snackbar message. A dialog does not show in a frame, so this is where it is pinned.
 */
@RunWith(RobolectricTestRunner::class)
class ServerScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val info = ServerInfo("truenas", "10.11.6", "http://192.168.1.10:8096", "X64", "Linux", false, true, true)
    private var dismissed = 0

    private fun show(state: ServerUiState.Ready) {
        compose.setContent { JellyfinTheme { ServerScreen(state, ServerActions(onDismissRestart = { dismissed++ })) } }
    }

    @Test
    fun `a restart the server did not come back from is a dialog that OK dismisses once`() {
        show(ServerUiState.Ready(info, restart = Restart.NotBack))

        compose.onNodeWithText("The server hasn't come back").assertExists()
        compose.onNodeWithText("Check it where it runs", substring = true).assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun `the not-back text is not also drawn on the page behind the dialog`() {
        show(ServerUiState.Ready(info, restart = Restart.NotBack))

        compose.onNodeWithText("The server hasn't come back after two minutes. Check it where it runs.").assertDoesNotExist()
    }

    @Test
    fun `a restart the server refused is a short message, with no dialog`() {
        show(ServerUiState.Ready(info, restart = Restart.Failed))

        compose.onNodeWithText("The server didn't take the restart. Try again.").assertExists()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }
}
