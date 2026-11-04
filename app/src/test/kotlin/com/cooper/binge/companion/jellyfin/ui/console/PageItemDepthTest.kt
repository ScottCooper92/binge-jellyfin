package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * For the pages that still keep an open item as their own state. A page beside the home has no Back of its own, since the home never leaves the screen. An item open inside the page
 * is not that: its Back returns to the page's list, so it keeps one.
 */
@RunWith(RobolectricTestRunner::class)
class PageItemDepthTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val closed = mutableListOf<String>()

    /** A page as the console's host shows it: the home beside it, so the page is not alone on screen. */
    private fun besideHome(open: Boolean) =
        compose.setContent {
            JellyfinTheme {
                CompositionLocalProvider(LocalIsSinglePaneNav provides false) {
                    PageItemDepth(open = open) {
                        TasksScreen(TasksUiState.Loading, TasksActions(onBack = { closed += "page" }))
                    }
                }
            }
        }

    @Test
    fun `beside the home a page with nothing open has no Back of its own`() {
        besideHome(open = false)
        compose.onAllNodesWithContentDescription("Back").assertCountEquals(0)
    }

    @Test
    fun `beside the home an open item keeps a Back that returns to the page's list`() {
        besideHome(open = true)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("page"), closed)
    }
}
