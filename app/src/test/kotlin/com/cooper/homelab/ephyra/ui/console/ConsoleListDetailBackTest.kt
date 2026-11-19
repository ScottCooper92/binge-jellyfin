package com.cooper.homelab.ephyra.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.nav.EphyraNavHost
import com.cooper.homelab.ephyra.ui.nav.closeTop
import com.cooper.homelab.ephyra.ui.nav.HubRoute
import com.cooper.homelab.ephyra.ui.nav.entries
import com.cooper.homelab.ephyra.ui.nav.openFromHome
import com.cooper.homelab.ephyra.ui.nav.rememberConsoleBackStack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real page BackHandlers inside the Navigation 3 host, with the page as the first entry beside the home. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
class ConsoleListDetailBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var current: NavBackStack<NavKey> = NavBackStack(HubRoute)

    private fun host(
        page: ConsolePage,
        content: @Composable (() -> Unit) -> Unit,
    ) = compose.setContent {
        JellyfinTheme {
            val stack = rememberConsoleBackStack { openFromHome(page) }
            current = stack
            EphyraNavHost(
                stack,
                signedIn = true,
                onBack = { stack.closeTop() },
                signedOut = {},
                home = { Text("Console home") },
                defaultPage = { Text("Default page") },
                page = { content { stack.closeTop() } },
            )
        }
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `two Backs in one frame on the first page leave the home and default pane intact`() {
        host(ConsolePage.Logs) { leave -> LogsScreen(LogsUiState.Ready(emptyList()), LogsActions(onBack = leave)) }
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("Console home").assertIsDisplayed()
        compose.onNodeWithText("Default page").assertIsDisplayed()
        assertEquals(0, current.entries().size)
    }
}
