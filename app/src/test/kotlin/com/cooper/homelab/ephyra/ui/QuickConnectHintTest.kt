package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import okhttp3.HttpUrl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sign-in step chooses its method on tabs. Quick Connect's hint is the line its tab shows in place of fields: never on the
 * password's tab, and never when the server has said Quick Connect is off, which leaves no such tab.
 */
@RunWith(RobolectricTestRunner::class)
class QuickConnectHintTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val hint = "Signs you in from a Jellyfin app you're already signed in to. You get a code to enter there."

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
        selected: Boolean = false,
        on: Boolean = true,
        error: SetupError? = null,
    ) = UiState.SignIn(
        baseUrl = "http://192.168.1.20:8096",
        server = server,
        quickConnectError = error,
        quickConnectOn = on,
        quickConnectSelected = selected,
    )

    private fun show(state: UiState.SignIn) = compose.setContent { JellyfinTheme { JellyfinRoot(state, Actions()) } }

    @Test
    fun `it is said on the Quick Connect tab`() {
        show(signIn(selected = true))
        compose.onNodeWithText(hint).assertExists()
    }

    @Test
    fun `it is not said on the password tab`() {
        show(signIn())
        compose.onNodeWithText("Quick Connect").assertExists()
        compose.onAllNodesWithText(hint).assertCountEquals(0)
    }

    @Test
    fun `it is not said when the server has said Quick Connect is off`() {
        show(signIn(on = false, error = SetupError.QuickConnectOff))
        compose.onAllNodesWithText("Quick Connect").assertCountEquals(0)
        compose.onAllNodesWithText(hint).assertCountEquals(0)
    }
}
