package com.cooper.homelab.ephyra.ui.console

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.ui.nav.EphyraNavHost
import com.cooper.homelab.ephyra.ui.nav.HubRoute
import com.cooper.homelab.ephyra.ui.nav.closeAll
import com.cooper.homelab.ephyra.ui.nav.closeTop
import com.cooper.homelab.ephyra.ui.nav.entries
import com.cooper.homelab.ephyra.ui.nav.keeping
import com.cooper.homelab.ephyra.ui.nav.openItem
import com.cooper.homelab.ephyra.ui.nav.openPage
import com.cooper.homelab.ephyra.ui.nav.rememberConsoleBackStack
import com.cooper.homelab.ephyra.ui.nav.top
import com.cooper.homelab.ephyra.ui.nav.topItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The console's navigation on Navigation 3: Back, a page's own Back, what a page and the home keep while they are
 * on the stack, and what survives the Activity being made again (a rotation).
 */
@RunWith(RobolectricTestRunner::class)
class ConsoleNavHostTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    /** A page's view model: it records when it is cleared, which is when its entry leaves the stack. */
    class PageModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private val models = mutableMapOf<ConsolePage, PageModel>()
    private var detailOpen by mutableStateOf(false)
    private var backStack: NavBackStack<NavKey> = NavBackStack(HubRoute)
    private var content: @Composable () -> Unit = {}

    private fun host(seed: NavBackStack<NavKey>.() -> Unit = {}) {
        content = {
            val stack = rememberConsoleBackStack(seed)
            backStack = stack
            EphyraNavHost(
                backStack = stack,
                signedIn = true,
                onBack = { stack.closeTop() },
                signedOut = {},
                defaultPage = {},
                home = {
                    var scrolled by rememberSaveable { mutableIntStateOf(0) }
                    Column {
                        Text("home $scrolled", Modifier.clickable { scrolled++ })
                        Text("open plugins", Modifier.clickable { stack.openPage(ConsolePage.Plugins) })
                    }
                },
                page = { entry ->
                    models[entry.page] = viewModel { PageModel() }
                    Column {
                        Text("page ${entry.page.name}")
                        Text("open server", Modifier.clickable { stack.openPage(ConsolePage.Server) })
                        if (entry.page == ConsolePage.Plugins && detailOpen) {
                            Text("detail")
                            // A list's open detail closes before the page does.
                            BackHandler { detailOpen = false }
                        }
                    }
                },
            )
        }
        compose.setContent(content)
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `a page shows at once, and Back returns to the one under it and then to the home`() {
        host()
        compose.onNodeWithText("open plugins").performClick()
        compose.onNodeWithText("page Plugins").assertIsDisplayed()
        compose.onNodeWithText("open plugins").assertDoesNotExist()
        compose.onNodeWithText("open server").performClick()
        compose.onNodeWithText("page Server").assertIsDisplayed()
        back()
        compose.onNodeWithText("page Plugins").assertIsDisplayed()
        back()
        compose.onNodeWithText("open plugins").assertIsDisplayed()
    }

    @Test
    fun `a page's own Back closes what it has open before Back closes the page`() {
        host { openPage(ConsolePage.Plugins) }
        detailOpen = true
        compose.waitForIdle()
        compose.onNodeWithText("detail").assertIsDisplayed()
        back()
        compose.onNodeWithText("detail").assertDoesNotExist()
        compose.onNodeWithText("page Plugins").assertIsDisplayed()
        back()
        compose.onNodeWithText("open plugins").assertIsDisplayed()
    }

    @Test
    fun `a page keeps its view model while another is open over it and loses it when it closes`() {
        host()
        compose.onNodeWithText("open plugins").performClick()
        compose.waitForIdle()
        val plugins = models.getValue(ConsolePage.Plugins)
        compose.onNodeWithText("open server").performClick()
        compose.waitForIdle()
        assertFalse(plugins.cleared)
        back()
        compose.waitForIdle()
        assertSame(plugins, models.getValue(ConsolePage.Plugins))
        back()
        compose.waitForIdle()
        assertTrue(plugins.cleared)
        // The same page opened again is a new opening, so a new view model.
        compose.onNodeWithText("open plugins").performClick()
        compose.waitForIdle()
        assertNotSame(plugins, models.getValue(ConsolePage.Plugins))
    }

    @Test
    fun `the home's saved state is there when it comes back from a page`() {
        host()
        compose.onNodeWithText("home 0").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("open plugins").performClick()
        compose.waitForIdle()
        back()
        compose.waitForIdle()
        compose.onNodeWithText("home 1").assertIsDisplayed()
    }

    @Test
    fun `an open page and its view model survive the Activity being made again`() {
        host()
        compose.onNodeWithText("open plugins").performClick()
        compose.waitForIdle()
        val before = models.getValue(ConsolePage.Plugins)
        compose.scenario.recreate()
        compose.setContent(content)
        compose.waitForIdle()
        compose.onNodeWithText("page Plugins").assertIsDisplayed()
        assertSame(before, models.getValue(ConsolePage.Plugins))
        assertFalse(before.cleared)
        assertEquals(ConsolePage.Plugins, backStack.top?.page)
    }

    @Test
    fun `an open page, its item and the row it was opened on survive the Activity being made again`() {
        host {
            openPage(ConsolePage.Plugins, "row")
            openItem("scan")
        }
        compose.waitForIdle()
        compose.scenario.recreate()
        compose.setContent(content)
        compose.waitForIdle()
        assertEquals(ConsolePage.Plugins, backStack.top?.page)
        assertEquals("scan", backStack.topItem?.item)
        assertEquals("row", backStack.entries().first().row)
    }

    @Test
    fun `a page the server or the user lost leaves the stack, and its view model goes with it`() {
        host()
        compose.onNodeWithText("open plugins").performClick()
        compose.waitForIdle()
        val plugins = models.getValue(ConsolePage.Plugins)
        compose.onNodeWithText("open server").performClick()
        compose.waitForIdle()
        val server = models.getValue(ConsolePage.Server)
        compose.runOnIdle { backStack.keeping { it != ConsolePage.Plugins } }
        compose.waitForIdle()
        assertTrue(plugins.cleared)
        assertFalse(server.cleared)
        // Back skips the page that is gone: it goes straight to the home.
        back()
        compose.waitForIdle()
        compose.onNodeWithText("open plugins").assertIsDisplayed()
        assertTrue(server.cleared)
    }

    @Test
    fun `signing out closes every page and its view model`() {
        host()
        compose.onNodeWithText("open plugins").performClick()
        compose.waitForIdle()
        val plugins = models.getValue(ConsolePage.Plugins)
        compose.runOnIdle { backStack.closeAll() }
        compose.waitForIdle()
        assertTrue(plugins.cleared)
        compose.onNodeWithText("open plugins").assertIsDisplayed()
    }
}
