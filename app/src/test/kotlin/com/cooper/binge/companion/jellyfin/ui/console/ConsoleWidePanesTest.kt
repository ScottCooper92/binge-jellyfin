package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.LocalPaneInnerEdge
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The console on a window wide enough for two panes: the home stays, and the page opens beside it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h800dp")
class ConsoleWidePanesTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var stackNow = ConsoleBackStack()
    private val seen = mutableMapOf<String, Triple<PaneEdge?, Boolean, Int>>()

    private fun host(signedIn: Boolean = true) {
        compose.setContent {
            var stack by rememberSaveable(stateSaver = ConsoleBackStack.Saver) { mutableStateOf(ConsoleBackStack()) }
            stackNow = stack
            ConsoleNavHost(
                stack = stack,
                signedIn = signedIn,
                onBack = { stack = stack.back() },
                home = {
                    seen["home"] = Triple(LocalPaneInnerEdge.current, LocalIsSinglePaneNav.current, LocalPaneDepth.current)
                    Column {
                        Text("home")
                        Text("open logs", Modifier.clickable { stack = stack.openFromHome(ConsolePage.Logs) })
                        Text("open tasks", Modifier.clickable { stack = stack.openFromHome(ConsolePage.Tasks) })
                    }
                },
                defaultPage = {
                    seen["default"] = Triple(LocalPaneInnerEdge.current, LocalIsSinglePaneNav.current, LocalPaneDepth.current)
                    Text("default page")
                },
                page = { entry ->
                    seen["page"] = Triple(LocalPaneInnerEdge.current, LocalIsSinglePaneNav.current, LocalPaneDepth.current)
                    Text("page ${entry.page.name}")
                },
            )
        }
    }

    @Test
    fun `the default page fills the detail pane while no page is open`() {
        host()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("default page").assertIsDisplayed()
    }

    @Test
    fun `a page opens beside the home, and another from the home replaces it`() {
        host()
        compose.onNodeWithText("open logs").performClick()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("page Logs").assertIsDisplayed()
        compose.onNodeWithText("open tasks").performClick()
        compose.onNodeWithText("page Tasks").assertIsDisplayed()
        assertEquals(listOf(ConsolePage.Tasks), stackNow.entries.map { it.page })
    }

    @Test
    fun `before sign-in the home takes the whole window and no default page shows`() {
        host(signedIn = false)
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("default page").assertDoesNotExist()
    }

    @Test
    fun `each pane knows the edge it shares with the one beside it`() {
        host()
        assertEquals(PaneEdge.End, seen["home"]?.first)
        assertEquals(PaneEdge.Start, seen["default"]?.first)
        compose.onNodeWithText("open logs").performClick()
        compose.waitForIdle()
        assertEquals(PaneEdge.Start, seen["page"]?.first)
        assertEquals(false, seen["page"]?.second)
    }

    @Test
    fun `before sign-in the home has no inner edge`() {
        host(signedIn = false)
        assertEquals(null, seen["home"]?.first)
    }
}
