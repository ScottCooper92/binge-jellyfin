package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The plugins page given two panes' width: the tabs' lists and the open plugin side by side. */
@RunWith(RobolectricTestRunner::class)
class PluginsSplitTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun plugin(
        id: String,
        name: String,
    ) = ServerPlugin(id, name, "1.0.0.0", "About $name.", PluginStatus.Active, canUninstall = true, hasSettings = false)

    private val plugins = listOf(plugin("a", "AniDB"), plugin("b", "Playback Reporting"))

    private fun show(
        open: String?,
        split: Boolean,
    ) = compose.setContent {
        JellyfinTheme {
            CompositionLocalProvider(LocalPageSpansTwoPanes provides split) {
                PluginsScreen(
                    PluginsUiState.Ready(plugins, open = open),
                    CatalogUiState.Loading,
                    RepositoriesUiState.Loading,
                    PluginsActions(),
                    CatalogActions(),
                    RepositoriesActions(),
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `beside the list the open plugin shows under the page's own title, its row marked`() {
        show("a", split = true)
        compose.onNodeWithText("Plugins").assertIsDisplayed()
        compose.onNodeWithText("Playback Reporting").assertIsDisplayed()
        // The open plugin is in the list, marked, and again as the heading of its page.
        compose.onAllNodesWithText("AniDB").assertCountEquals(2)
        compose.onAllNodes(hasText("AniDB") and isSelected()).assertCountEquals(1)
        compose.onAllNodes(hasText("Playback Reporting") and isSelected()).assertCountEquals(0)
        compose.onNodeWithText("About AniDB.").assertIsDisplayed()
    }

    @Test
    fun `beside the list with nothing open the detail says to pick one`() {
        show(null, split = true)
        compose.onNodeWithText("Playback Reporting").assertIsDisplayed()
        compose.onNodeWithText("Select a plugin or a package to see its details.").assertIsDisplayed()
    }

    @Test
    fun `on its own an open plugin replaces the tabs, titled with its name`() {
        show("a", split = false)
        compose.onNodeWithText("AniDB").assertIsDisplayed()
        compose.onAllNodesWithText("Playback Reporting").assertCountEquals(0)
        compose.onAllNodesWithText("Select a plugin or a package to see its details.").assertCountEquals(0)
    }
}
