package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Before a server is connected a user can still report a problem: Settings is out of reach, so the menu is on the screen. */
@RunWith(RobolectricTestRunner::class)
class ReportProblemActionTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val server =
        PublicServerInfo(
            serverName = "Living room",
            version = "10.11.6",
            serverId = "a1",
            baseUrl =
                okhttp3.HttpUrl
                    .Builder()
                    .scheme("http")
                    .host("192.168.1.20")
                    .port(8096)
                    .build(),
        )

    @Test
    fun `the menu opens the bug form with the app and the device and no server`() {
        val opened = mutableListOf<String>()
        compose.setContent { JellyfinTheme { ReportProblemAction(open = { opened += it }) } }
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Report a problem").performClick()
        val url = opened.single()
        assertTrue(url, url.startsWith("https://github.com/ScottCooper92/app-feedback/issues/new?template=ephyra-bug.yml"))
        assertTrue(url, url.contains("app-version="))
        assertFalse(url, url.contains("server="))
    }

    @Test
    fun `the server step has the menu`() {
        compose.setContent { JellyfinTheme { JellyfinRoot(UiState.EnterServer(), Actions()) } }
        compose.onNodeWithContentDescription("More options").assertExists()
    }

    @Test
    fun `the sign in step has the menu`() {
        compose.setContent {
            JellyfinTheme {
                JellyfinRoot(
                    UiState.SignIn(baseUrl = "http://192.168.1.20:8096", server = server),
                    Actions(),
                )
            }
        }
        compose.onNodeWithContentDescription("More options").assertExists()
    }

    @Test
    fun `the menu is closed until it is asked for`() {
        compose.setContent { JellyfinTheme { ReportProblemAction(open = {}) } }
        compose.onAllNodesWithText("Report a problem").assertCountEquals(0)
    }
}
