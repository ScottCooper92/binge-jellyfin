package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.PublicUser
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The sign-in step is a pager of its two ways in: a swipe or a tap on a tab moves between them. Each page is drawn for its own way, so the
 * listed accounts and the reset of a forgotten password are on the password's page alone, also while a swipe is half way and the chosen
 * way is still the password.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class SignInSwipeTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val accounts = "or pick your account"
    private val forgot = "Forgot password?"
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

    private fun signIn(quickConnect: Boolean) =
        UiState.SignIn(
            baseUrl = "http://192.168.1.20:8096",
            server = server,
            publicUsers = listOf(PublicUser("u1", "Mum", hasPassword = true), PublicUser("u2", "Dad", hasPassword = true)),
            quickConnectOn = true,
            quickConnectSelected = quickConnect,
        )

    private var state by mutableStateOf(signIn(quickConnect = false))
    private val selected = mutableListOf<Boolean>()

    /** The view model's part: the way a page settled on or a tab chose becomes the selected way, as it does in the app. */
    private fun show(follows: Boolean = true) =
        compose.setContent {
            val actions =
                Actions(
                    onSelectMethod = { quickConnect ->
                        selected += quickConnect
                        if (follows) state = signIn(quickConnect)
                    },
                )
            JellyfinTheme { JellyfinRoot(state, actions) }
        }

    private fun count(text: String) = compose.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().size

    @Test
    fun `the listed accounts and the reset are on the password page`() {
        show()
        compose.onAllNodesWithText(accounts, ignoreCase = true).assertCountEquals(1)
        compose.onAllNodesWithText(forgot).assertCountEquals(1)
    }

    @Test
    fun `a swipe to Quick Connect selects it and takes the accounts and the reset away`() {
        show()
        compose.onNodeWithText("Mum").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf(true), selected)
        compose.onNodeWithText(hint).assertExists()
        compose.onAllNodesWithText(accounts, ignoreCase = true).assertCountEquals(0)
        compose.onAllNodesWithText(forgot).assertCountEquals(0)
    }

    @Test
    fun `a tap on the Quick Connect tab selects it`() {
        show()
        compose.onNodeWithText("Quick Connect").performClick()
        compose.waitForIdle()
        assertEquals(listOf(true), selected)
        compose.onNodeWithText(hint).assertExists()
    }

    @Test
    fun `half way through a swipe the Quick Connect page has no accounts and no reset`() {
        show(follows = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Mum").performTouchInput {
            down(center)
            moveBy(Offset(-width / 2f, 0f))
        }
        compose.mainClock.advanceTimeBy(FRAMES_MILLIS)
        // The Quick Connect page is on screen beside the password's, while the chosen way is still the password.
        compose.onNodeWithText(hint).assertExists()
        assertEquals(1, count(accounts))
        assertEquals(1, count(forgot))
    }

    private companion object {
        const val FRAMES_MILLIS = 300L
    }
}
