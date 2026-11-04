package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A landscape tablet: the home, a page's list and what it opened are three panes, a third of the window each. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
class ConsoleThreePaneTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun host() {
        compose.setContent {
            var stack by rememberSaveable(stateSaver = ConsoleBackStack.Saver) { mutableStateOf(ConsoleBackStack()) }
            ConsoleNavHost(
                stack = stack,
                signedIn = true,
                onBack = { stack = stack.back() },
                home = { Column { Text("home", Modifier.clickable { stack = stack.openFromHome(ConsolePage.Plugins) }) } },
                defaultPage = { Text("default page") },
                page = {
                    ListDetailBody(detailOpen = false, list = { Text("the list") }, detail = { Text("the detail") })
                },
            )
        }
    }

    @Test
    fun `the default page fills the pane beside the home`() {
        host()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("default page").assertIsDisplayed()
    }

    @Test
    fun `a page with a list and a detail shows both beside the home, each a third of the window`() {
        host()
        compose.onNodeWithText("home").performClick()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("the list").assertIsDisplayed()
        compose.onNodeWithText("the detail").assertIsDisplayed()
        val home = compose.onNodeWithText("home").getUnclippedBoundsInRoot().left
        val list = compose.onNodeWithText("the list").getUnclippedBoundsInRoot().left
        val detail = compose.onNodeWithText("the detail").getUnclippedBoundsInRoot().left
        // Evenly spaced, within the panes' own content insets: the same step from the home to the list as from the list to the detail.
        assertEquals((list - home).value, (detail - list).value, 8f)
    }

    @Test
    fun `a list-detail body on a page of one pane shows the detail only while one is open`() {
        var open by mutableStateOf(false)
        compose.setContent {
            JellyfinTheme {
                CompositionLocalProvider(LocalPageSpansTwoPanes provides false) {
                    ListDetailBody(detailOpen = open, list = { Text("the list") }, detail = { Text("the detail") })
                }
            }
        }
        compose.onNodeWithText("the list").assertIsDisplayed()
        compose.runOnIdle { open = true }
        compose.onNodeWithText("the detail").assertIsDisplayed()
        compose.onNodeWithText("the list").assertDoesNotExist()
    }
}
