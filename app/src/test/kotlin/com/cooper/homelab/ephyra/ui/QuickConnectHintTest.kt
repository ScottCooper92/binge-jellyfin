package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import okhttp3.HttpUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sign-in step tells someone with no password to use Quick Connect, and says so only before Quick Connect is tried: never once
 * the server has said it is off, never while a code is being asked for, and not after a code has expired or Quick Connect failed.
 */
@RunWith(RobolectricTestRunner::class)
class QuickConnectHintTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val hint = "No password here? Quick Connect signs you in from a Jellyfin app you're already signed in to."

    private val server =
        PublicServerInfo(
            serverName = "Living room",
            version = "10.11.6",
            serverId = "a1",
            baseUrl =
                HttpUrl
                    .Builder()
                    .scheme("http")
                    .host("192.168.1.20")
                    .port(8096)
                    .build(),
        )

    private fun signIn(
        error: SetupError? = null,
        gettingCode: Boolean = false,
    ) = UiState.SignIn(baseUrl = "http://192.168.1.20:8096", server = server, quickConnectError = error, gettingCode = gettingCode)

    private fun show(state: UiState.SignIn) = compose.setContent { JellyfinTheme { JellyfinRoot(state, Actions()) } }

    /** The step is a scrolling column, so what is below the Quick Connect button is scrolled to before it is looked for. */
    private fun scrollToQuickConnect() = compose.onNode(hasScrollAction()).performScrollToNode(hasText("Use Quick Connect"))

    @Test
    fun `it is said under the button before Quick Connect is tried`() {
        show(signIn())
        scrollToQuickConnect()
        compose.onNodeWithText(hint).assertExists()
    }

    @Test
    fun `it is not said when the server has said Quick Connect is off`() {
        show(signIn(error = SetupError.QuickConnectOff))
        scrollToQuickConnect()
        compose.onNodeWithText("Quick Connect is turned off on this server").assertExists()
        compose.onAllNodesWithText(hint).assertCountEquals(0)
    }

    @Test
    fun `it is not said when the code has expired`() {
        show(signIn(error = SetupError.QuickConnectExpired))
        scrollToQuickConnect()
        compose.onAllNodesWithText(hint).assertCountEquals(0)
    }

    @Test
    fun `it is not said while a code is being asked for`() {
        show(signIn(gettingCode = true))
        // The button shows progress in place of its label, so the step's last row is the thing to scroll to.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Forgot password?"))
        compose.onAllNodesWithText(hint).assertCountEquals(0)
    }
}
