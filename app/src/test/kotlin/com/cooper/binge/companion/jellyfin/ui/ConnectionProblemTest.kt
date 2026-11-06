package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A saved server that cannot be reached or read takes the home's place, with Try again, Edit server details and Sign out. */
@RunWith(RobolectricTestRunner::class)
class ConnectionProblemTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val session =
        JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private fun state(
        health: ConnectionHealth,
        refreshing: Boolean = false,
    ) = UiState.SignedIn(
        session,
        titles = null,
        refreshing = refreshing,
        health = health,
        account = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":true}""").jsonObject),
    )

    @Test
    fun `an unreachable server shows its problem and the three ways out, not the home`() {
        compose.setContent { JellyfinTheme { JellyfinRoot(state(ConnectionHealth.Unreachable), Actions()) } }
        compose.onNodeWithText("Can't reach your server").assertExists()
        compose.onNodeWithText("Try again").assertExists()
        compose.onNodeWithText("Edit server details").assertExists()
        compose.onNodeWithText("Sign out").assertExists()
        compose.onAllNodesWithText("Administration").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Your account").assertCountEquals(0)
    }

    @Test
    fun `a server that answered something unusable says so`() {
        compose.setContent { JellyfinTheme { JellyfinRoot(state(ConnectionHealth.CouldNotLoad), Actions()) } }
        compose.onNodeWithText("Couldn't load your server").assertExists()
    }

    @Test
    fun `a healthy or still-checking server shows the home`() {
        compose.setContent { JellyfinTheme { JellyfinRoot(state(ConnectionHealth.Checking), Actions()) } }
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
        compose.onAllNodesWithText("Can't reach your server").assertCountEquals(0)
    }

    @Test
    fun `each way out reaches its action`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            JellyfinTheme {
                JellyfinRoot(
                    state(ConnectionHealth.Unreachable),
                    Actions(onRefresh = { calls += "retry" }, onChangeServer = { calls += "edit" }, onAskSignOut = { calls += "signout" }),
                )
            }
        }
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Edit server details").performClick()
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(listOf("retry", "edit", "signout"), calls)
    }

    @Test
    fun `a retry in flight shows a spinner in place of its label and swallows taps`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            JellyfinTheme {
                JellyfinRoot(
                    state(ConnectionHealth.Unreachable, refreshing = true),
                    Actions(onRefresh = { calls += "retry" }, onChangeServer = { calls += "edit" }),
                )
            }
        }
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
        // The retry is the first button on the page; the others stay open while it works.
        compose.onAllNodes(hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Edit server details").performClick()
        assertEquals(listOf("edit"), calls)
    }
}
