package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The client's shell on a landscape tablet: the home, a page and its item are a third each, inside the Server tab, with the rail. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
class ShellThreePaneTest : ShellHostTestBase() {
    @Test
    fun `the home, a page and its item show together with the rail`() {
        host()
        tap("tab_server")
        click("open tasks")
        assertShown("console home")
        assertShown("page Tasks")
        compose.runOnUiThread { stack?.openItem("scan") }
        compose.waitForIdle()
        assertShown("console home")
        assertShown("page Tasks")
        assertShown("item scan")
        assertBar(true)
    }

    @Test
    fun `Back closes the item and keeps the page, then the page, and keeps the shell`() {
        host()
        tap("tab_server")
        click("open tasks")
        compose.runOnUiThread { stack?.openItem("scan") }
        compose.waitForIdle()
        assertFalse(pressBack())
        assertShown("page Tasks")
        assertAbsent("item scan")
        assertFalse(pressBack())
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertShown("console home")
    }

    @Test
    fun `a page and its item are parked together, and return together`() {
        host()
        tap("tab_server")
        click("open tasks")
        compose.runOnUiThread { stack?.openItem("scan") }
        compose.waitForIdle()
        val open = keys().drop(1)
        tap("tab_search")
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        tap("tab_server")
        assertEquals(open, keys().drop(1))
        assertShown("item scan")
        assertEquals(ConsolePage.Tasks, pageKeys().single().page)
    }
}
