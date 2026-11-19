package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The client's shell on a window wide enough for two panes: the rail stays, and a page opens beside the home inside the Server tab. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h800dp")
class ShellWideTest : ShellHostTestBase() {
    @Test
    fun `a page opens beside the home and the bar stays`() {
        host()
        tap("tab_server")
        assertShown("console home")
        assertShown("default page")
        click("open logs")
        assertShown("console home")
        assertShown("page Logs")
        assertBar(true)
        assertAbsent("default page")
    }

    @Test
    fun `Back closes the page and keeps the shell, then Back at the Server tab goes to the start tab`() {
        host()
        tap("tab_server")
        click("open logs")
        assertFalse(pressBack())
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertShown("console home")
        assertFalse(pressBack())
        assertEquals(EphyraTab.Home, tabs?.selected)
    }

    @Test
    fun `a page is parked while another tab shows, and is back as it was when the Server tab returns`() {
        host()
        tap("tab_server")
        click("open logs")
        val page = pageKeys().single()
        tap("tab_libraries")
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertShown("This screen is on its way.")
        tap("tab_server")
        assertEquals(listOf(page), pageKeys())
        assertShown("page Logs")
    }

    @Test
    fun `a page opened from the home replaces the one open, with the shell kept`() {
        host()
        tap("tab_server")
        click("open logs")
        click("open tasks")
        assertEquals(listOf(ConsolePage.Tasks), pageKeys().map { it.page })
        assertShown("page Tasks")
        assertAbsent("page Logs")
        assertEquals(ShellRoute, keys().first())
    }

    @Test
    fun `folding to a narrow window moves no key`() {
        host()
        tap("tab_server")
        click("open logs")
        val before = keys()
        RuntimeEnvironment.setQualifiers("w400dp-h800dp")
        compose.scenario.recreate()
        host()
        assertEquals(before, keys())
        assertShown("page Logs")
        assertBar(false)
        RuntimeEnvironment.setQualifiers("w900dp-h800dp")
        compose.scenario.recreate()
        host()
        assertEquals(before, keys())
        assertShown("console home")
        assertShown("page Logs")
        assertBar(true)
    }

    @Test
    fun `a page parked in a narrow window comes back on the Server tab`() {
        host()
        tap("tab_server")
        click("open logs")
        val page = pageKeys().single()
        tap("tab_libraries")
        RuntimeEnvironment.setQualifiers("w400dp-h800dp")
        compose.scenario.recreate()
        host()
        tap("tab_server")
        assertEquals(page.opening, pageKeys().single().opening)
        assertNotEquals(emptyList<PageRoute>(), pageKeys())
        assertShown("page Logs")
    }

    @Test
    fun `with the gate off the console is today's with the home beside the page and no rail`() {
        gate = false
        host()
        assertEquals(listOf<NavKey>(HubRoute), keys())
        assertBar(false)
        click("open logs")
        assertShown("console home")
        assertShown("page Logs")
    }

    @Test
    fun `a page parked on the Server tab does not come back after the session ends`() {
        host()
        tap("tab_server")
        click("open logs")
        tap("tab_libraries")
        assertEquals(2, tabs?.stack(EphyraTab.Server)?.size)
        compose.runOnUiThread { connected = false }
        compose.waitForIdle()
        assertEquals(1, tabs?.stack(EphyraTab.Server)?.size)
        compose.runOnUiThread { connected = true }
        compose.waitForIdle()
        assertEquals(listOf<NavKey>(ShellRoute), keys())
        assertEquals(EphyraTab.Home, tabs?.selected)
        tap("tab_server")
        assertEquals(emptyList<PageRoute>(), pageKeys())
        assertAbsent("page Logs")
        assertShown("console home")
    }

    @Test
    fun `a library covers the rail and the whole window, and Back brings the shell back`() {
        hostWithLibraries()
        tap("tab_libraries")
        click("open movies")
        assertShown("library lib-movies")
        assertBar(false)
        assertFalse(pressBack())
        assertBar(true)
        assertShown("open movies")
        assertEquals(listOf<NavKey>(ShellRoute), keys())
    }

    @Test
    fun `a page parked on the Server tab is still there after a library was opened and closed`() {
        hostWithLibraries()
        tap("tab_server")
        click("open tasks")
        tap("tab_libraries")
        click("open movies")
        assertFalse(pressBack())
        assertEquals(EphyraTab.Libraries, tabs?.selected)
        tap("tab_server")
        assertEquals(listOf(ConsolePage.Tasks), pageKeys().map { it.page })
    }
}
