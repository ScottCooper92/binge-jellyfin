package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.paneShowsBack
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An item open in a page is an entry of its own: its Back, its depth and its view models come from the stack. */
abstract class ItemEntriesBase {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    class Shared : ViewModel()

    protected var stackNow = ConsoleBackStack()
    protected var pageModel: Shared? = null
    protected var itemModel: Shared? = null
    protected var itemDepth = -1
    protected var itemBack = false

    protected fun host(initial: ConsoleBackStack = ConsoleBackStack().openFromHome(ConsolePage.Tasks)) {
        compose.setContent {
            JellyfinTheme {
                var stack by rememberSaveable(stateSaver = ConsoleBackStack.Saver) { mutableStateOf(initial) }
                stackNow = stack
                ConsoleNavHost(
                    stack = stack,
                    signedIn = true,
                    onBack = { stack = stack.back() },
                    home = { Text("home") },
                    defaultPage = { Text("default page") },
                    page = { entry -> Page(entry) { stack = stack.openItem("scan") } },
                    item = { entry -> Item(entry) },
                )
            }
        }
    }

    @Composable
    private fun Page(
        entry: ConsoleEntry,
        open: () -> Unit,
    ) {
        pageModel = viewModel { Shared() }
        Text("page ${entry.page.name}", Modifier.clickable(onClick = open))
    }

    @Composable
    private fun Item(entry: ConsoleEntry) {
        itemModel = viewModel { Shared() }
        itemDepth = LocalPaneDepth.current
        itemBack = paneShowsBack(hubBeside = !LocalIsSinglePaneNav.current, paneDepth = LocalPaneDepth.current)
        Text("item ${entry.item}")
    }

    protected fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
}

/** A window with two panes, as an unfolded Fold: the item takes the pane the page had, and keeps a Back of its own. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h800dp")
class ConsoleItemEntriesTwoPaneTest : ItemEntriesBase() {
    @Test
    fun `an item replaces its page beside the home, and has a Back arrow`() {
        host()
        compose.onNodeWithText("page Tasks").performClick()
        compose.onNodeWithText("item scan").assertIsDisplayed()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onAllNodesWithText("page Tasks").assertCountEquals(0)
        assertEquals(2, itemDepth)
        assertEquals(true, itemBack)
    }

    @Test
    fun `Back closes the item, then the page, as the stack pops`() {
        host()
        compose.onNodeWithText("page Tasks").performClick()
        back()
        compose.waitForIdle()
        compose.onNodeWithText("page Tasks").assertIsDisplayed()
        assertEquals(listOf(ConsolePage.Tasks), stackNow.entries.map { it.page })
        back()
        compose.waitForIdle()
        assertEquals(emptyList<ConsolePage>(), stackNow.entries.map { it.page })
    }

    @Test
    fun `a page and its item share the view model, the item opened over the page`() {
        host()
        compose.onNodeWithText("page Tasks").performClick()
        compose.waitForIdle()
        assertNotNull(pageModel)
        assertSame(pageModel, itemModel)
    }
}

/** A landscape tablet: the home, the page and its item are three panes, and none of them offers a Back of its own. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
class ConsoleItemEntriesThreePaneTest : ItemEntriesBase() {
    @Test
    fun `the page stays beside its item, with the home`() {
        host()
        compose.onNodeWithText("page Tasks").performClick()
        compose.onNodeWithText("home").assertIsDisplayed()
        compose.onNodeWithText("page Tasks").assertIsDisplayed()
        compose.onNodeWithText("item scan").assertIsDisplayed()
        assertEquals(1, itemDepth)
        assertEquals(false, itemBack)
    }

    @Test
    fun `while no item is open the third pane says to pick one`() {
        host()
        compose.onNodeWithText("page Tasks").assertIsDisplayed()
        compose.onNodeWithText("Select a task to see its triggers.").assertIsDisplayed()
    }

    @Test
    fun `Back closes the item and leaves the page and the home`() {
        host()
        compose.onNodeWithText("page Tasks").performClick()
        back()
        compose.waitForIdle()
        compose.onNodeWithText("page Tasks").assertIsDisplayed()
        compose.onNodeWithText("Select a task to see its triggers.").assertIsDisplayed()
    }
}
