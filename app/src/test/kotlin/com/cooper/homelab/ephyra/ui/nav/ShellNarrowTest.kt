package com.cooper.homelab.ephyra.ui.nav

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The client's shell on a phone: the bar over the tab roots, and what a tab opens covers it. */
@RunWith(RobolectricTestRunner::class)
class ShellNarrowTest : ShellHostTestBase() {
    @Test
    fun `the client opens on the Home tab with the bar showing, and the shell is the root`() {
        host()
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertBar(true)
        assertShown("This screen is on its way.")
        assertEquals(EphyraTab.Home, tabs?.selected)
    }

    @Test
    fun `a tap on the Server tab shows the console's home`() {
        host()
        tap("tab_server")
        assertShown("console home")
        assertEquals(EphyraTab.Server, tabs?.selected)
    }

    @Test
    fun `Back at another tab's root goes to the start tab, and Back at the start tab leaves the app`() {
        host()
        tap("tab_server")
        assertFalse(pressBack())
        assertEquals(EphyraTab.Home, tabs?.selected)
        assertTrue(pressBack())
    }

    @Test
    fun `a page opened from the Server tab covers the bar, and Back brings the bar and the home back`() {
        host()
        tap("tab_server")
        click("open logs")
        assertShown("page Logs")
        assertBar(false)
        assertEquals(listOf(ConsolePage.Logs), pageKeys().map { it.page })
        assertFalse(pressBack())
        assertBar(true)
        assertShown("console home")
        assertEquals(listOf<NavKey>(ShellRoute), keys())
    }

    @Test
    fun `an item opened in a page covers the bar, and Back walks item, page, home`() {
        host()
        tap("tab_server")
        click("open tasks")
        compose.runOnUiThread { stack?.openItem("scan") }
        compose.waitForIdle()
        assertShown("item scan")
        assertFalse(pressBack())
        assertShown("page Tasks")
        assertFalse(pressBack())
        assertShown("console home")
        assertBar(true)
    }

    @Test
    fun `a page the account loses closes and leaves the shell, as for a user with fewer pages`() {
        host()
        tap("tab_server")
        click("open logs")
        compose.runOnUiThread { stack?.keeping { it != ConsolePage.Logs } }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertBar(true)
        assertShown("console home")
    }

    @Test
    fun `the selected tab and the open page survive the Activity being made again`() {
        host()
        tap("tab_server")
        click("open logs")
        val opening = pageKeys().single().opening
        compose.scenario.recreate()
        host()
        assertShown("page Logs")
        assertEquals(opening, pageKeys().single().opening)
        assertEquals(EphyraTab.Server, tabs?.selected)
    }

    @Test
    fun `with the gate off there is no shell, only the console, and Back from its home leaves the app`() {
        gate = false
        host()
        assertEquals(listOf<NavKey>(HubRoute), keys())
        assertBar(false)
        assertShown("console home")
        assertTrue(pressBack())
    }

    @Test
    fun `turning the gate off while a page is open starts over on the console alone`() {
        host()
        tap("tab_server")
        click("open logs")
        compose.runOnUiThread { gate = false }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(HubRoute), keys())
        assertBar(false)
        assertShown("console home")
        assertTrue(pressBack())
    }

    @Test
    fun `turning the gate on shows the shell on the start tab`() {
        gate = false
        host()
        compose.runOnUiThread { gate = true }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertBar(true)
        assertEquals(EphyraTab.Home, tabs?.selected)
        compose.onNodeWithTag("tab_home").performClick()
    }

    @Test
    fun `a stack restored under the shell is kept while the session is still loading, and when it resolves as signed in`() {
        host()
        tap("tab_server")
        click("open tasks")
        compose.runOnUiThread { stack?.openItem("scan") }
        compose.waitForIdle()
        val open = keys()
        assertEquals(3, open.size)
        assertEquals(ShellRoute, open.first())
        compose.scenario.recreate()
        // After a process death the state starts at Loading: nobody is signed in yet.
        connected = null
        host()
        assertEquals(open, keys())
        compose.runOnUiThread { connected = true }
        compose.waitForIdle()
        assertEquals(open, keys())
        assertShown("item scan")
    }

    @Test
    fun `a session that resolves as ended after a restore clears the stack under the shell`() {
        host()
        tap("tab_server")
        click("open tasks")
        compose.scenario.recreate()
        connected = null
        host()
        compose.runOnUiThread { connected = false }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(HomeRoute), keys())
    }
}
