package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
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

    private var current = ConsoleBackStack()

    private fun host(
        page: ConsolePage,
        content: @Composable (() -> Unit) -> Unit,
    ) = compose.setContent {
        JellyfinTheme {
            var stack by rememberSaveable(stateSaver = ConsoleBackStack.Saver) {
                mutableStateOf(ConsoleBackStack().openFromHome(page))
            }
            current = stack
            ConsoleNavHost(
                stack,
                signedIn = true,
                onBack = { stack = stack.back() },
                home = { Text("Console home") },
                defaultPage = { Text("Default page") },
                page = { content { stack = stack.back() } },
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
        assertEquals(0, current.entries.size)
    }
}
