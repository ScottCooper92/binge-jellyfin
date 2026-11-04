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
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
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
    fun `Back closes the plugin before the first page beside the home`() {
        val plugin = ServerPlugin("a", "AniDB", "1.0", "Metadata.", PluginStatus.Active, true, true)
        var state by mutableStateOf(PluginsUiState.Ready(listOf(plugin), open = "a"))
        host(ConsolePage.Plugins) { leave ->
            PluginsScreen(
                state,
                CatalogUiState.Ready(emptyList()),
                RepositoriesUiState.Ready(emptyList()),
                PluginsActions(onOpen = { state = state.copy(open = it?.id) }),
                CatalogActions(),
                RepositoriesActions(),
                onBack = leave,
            )
        }
        back()
        compose.onNodeWithText("Select a plugin or a package to see its details.").assertIsDisplayed()
        assertEquals(ConsolePage.Plugins, current.top?.page)
        back()
        compose.onNodeWithText("Default page").assertIsDisplayed()
        assertEquals(0, current.entries.size)
    }

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
