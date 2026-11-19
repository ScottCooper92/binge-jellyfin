package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rows the home's server group offers, by who is signed in. This group is built from the account's policy row by row, not from
 * the console's list of administrator pages, so each row is checked here against the policy that governs it (#790).
 */
@RunWith(RobolectricTestRunner::class)
class ServerGroupRowsTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val session = JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private fun show(policy: String) {
        val account = JellyfinAccount("sam", Json.parseToJsonElement("{$policy}").jsonObject)
        val state = UiState.SignedIn(session, titles = null, health = ConnectionHealth.Healthy, account = account)
        compose.setContent { JellyfinTheme { ServerGroup(state, Actions()) } }
    }

    @Test
    fun `a plain user with no Live TV management is offered no tuners and guide data page`() {
        show("\"IsAdministrator\":false,\"EnableLiveTvAccess\":true,\"EnableLiveTvManagement\":false")
        compose.onAllNodesWithText("Tuners and guide data").assertCountEquals(0)
        compose.onNodeWithText("Now playing").assertExists()
    }

    @Test
    fun `an administrator whose Live TV management is off is offered none either, as the server refuses them`() {
        show("\"IsAdministrator\":true,\"EnableLiveTvManagement\":false")
        compose.onAllNodesWithText("Tuners and guide data").assertCountEquals(0)
    }

    /**
     * The page follows the server's own rule (`docs/server-compatibility.md`): `LiveTvManagement` guards the writes and has no
     * administrator shortcut, so a plain user with it on may manage tuners and guide data, and the home offers the page. Jellyfin's
     * default policy for a new user has it on. If the page is to be an administrator's alone, this is the test that changes (#790).
     */
    @Test
    fun `a plain user whose Live TV management is on is offered the page, as the server allows them`() {
        show("\"IsAdministrator\":false,\"EnableLiveTvAccess\":true,\"EnableLiveTvManagement\":true")
        compose.onNodeWithText("Tuners and guide data").assertExists()
    }
}
