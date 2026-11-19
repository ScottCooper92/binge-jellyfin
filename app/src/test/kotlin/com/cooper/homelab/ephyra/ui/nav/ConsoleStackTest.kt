package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The console's stack functions on a [NavBackStack]: what Back does, and what stays when a page opens another. */
class ConsoleStackTest {
    private var next = 0
    private val ids: () -> Int = { next++ }

    private fun stack(open: NavBackStack<NavKey>.() -> Unit = {}) = NavBackStack<NavKey>(HubRoute).apply(open)

    private fun NavBackStack<NavKey>.pages() = entries().map { it.page }

    private fun NavBackStack<NavKey>.open(page: ConsolePage) = openPage(page, nextOpening = ids)

    @Test
    fun `a page opened from another returns to it on Back, and then to the home`() {
        val stack = stack { open(ConsolePage.Plugins) }
        val plugins = stack.top
        stack.open(ConsolePage.Server)
        assertEquals(ConsolePage.Server, stack.top?.page)
        stack.closeTop()
        // It is the same opening, so its view models are the ones it had.
        assertEquals(plugins, stack.top)
        stack.closeTop()
        assertNull(stack.top)
        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
        stack.closeTop()
        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
    }

    @Test
    fun `opening the page already on top does nothing, and every opening has its own number`() {
        val stack = stack { open(ConsolePage.Server) }
        val once = stack.toList()
        stack.open(ConsolePage.Server)
        assertEquals(once, stack.toList())
        stack.closeTop()
        stack.open(ConsolePage.Server)
        // The same page opened again is a new opening, so it never gets the old one's view models.
        assertEquals(setOf(1), stack.openings())
    }

    @Test
    fun `the same page lower on the stack opens again as an opening of its own`() {
        val stack = stack { open(ConsolePage.Plugins) }
        stack.open(ConsolePage.Server)
        stack.open(ConsolePage.Plugins)
        assertEquals(listOf(ConsolePage.Plugins, ConsolePage.Server, ConsolePage.Plugins), stack.pages())
        assertEquals(setOf(0, 1, 2), stack.openings())
    }

    @Test
    fun `an opening is never one already on the stack`() {
        val stack = stack { open(ConsolePage.Plugins) }
        var calls = 0
        stack.openPage(ConsolePage.Server) { if (calls++ == 0) 0 else 5 }
        assertEquals(setOf(0, 5), stack.openings())
    }

    @Test
    fun `closing every page leaves the home`() {
        val stack = stack { open(ConsolePage.Plugins) }
        stack.open(ConsolePage.Server)
        stack.closeAll()
        assertNull(stack.top)
        assertEquals(emptySet<Int>(), stack.openings())
        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
    }

    @Test
    fun `a page the server or the user lost leaves the stack, wherever it is, and Back skips it`() {
        val stack = stack { open(ConsolePage.ApproveCode) }
        stack.open(ConsolePage.Plugins)
        stack.open(ConsolePage.Server)
        stack.keeping { it != ConsolePage.Plugins }
        assertEquals(listOf(ConsolePage.ApproveCode, ConsolePage.Server), stack.pages())
        stack.closeTop()
        assertEquals(ConsolePage.ApproveCode, stack.top?.page)
    }

    @Test
    fun `keeping everything leaves the stack as it was`() {
        val stack = stack { open(ConsolePage.Plugins) }
        val before = stack.toList()
        stack.keeping { true }
        assertEquals(before, stack.toList())
    }

    @Test
    fun `a page opened from the home beside a page replaces it and what it opened`() {
        val stack = stack { open(ConsolePage.Logs) }
        stack.openItem("a")
        stack.openFromHome(ConsolePage.Tasks, nextOpening = ids)
        assertEquals(listOf(ConsolePage.Tasks), stack.pages())
        assertNull(stack.topItem)
    }

    @Test
    fun `the page already open at the bottom keeps its opening when the home opens it again`() {
        val stack = stack { open(ConsolePage.Logs) }
        val logs = stack.top
        stack.open(ConsolePage.Tasks)
        stack.openFromHome(ConsolePage.Logs, nextOpening = ids)
        assertEquals(listOf(logs), stack.entries())
    }

    @Test
    fun `an item opens above its page under the same opening, and Back returns to the page`() {
        val stack = stack { open(ConsolePage.Tasks) }
        val page = stack.top
        stack.openItem("scan")
        assertEquals("scan", stack.topItem?.item)
        assertEquals(page?.opening, stack.topItem?.opening)
        assertEquals(listOf<NavKey>(HubRoute, PageRoute(ConsolePage.Tasks, 0), ItemRoute(ConsolePage.Tasks, 0, "scan")), stack.toList())
        stack.closeTop()
        assertEquals(page, stack.top)
    }

    @Test
    fun `opening another item replaces the one open, and opening the one open does nothing`() {
        val stack = stack { open(ConsolePage.Tasks) }
        stack.openItem("scan")
        val one = stack.toList()
        stack.openItem("scan")
        assertEquals(one, stack.toList())
        stack.openItem("refresh")
        assertEquals(listOf("refresh"), stack.consoleKeys.filterIsInstance<ItemRoute>().map { it.item })
    }

    @Test
    fun `an item opened over another returns to the first on Back`() {
        val stack = stack { open(ConsolePage.Tasks) }
        stack.openItem("scan")
        stack.openSubItem("scan/triggers")
        assertEquals("scan/triggers", stack.topItem?.item)
        stack.closeTop()
        assertEquals("scan", stack.topItem?.item)
        stack.closeTop()
        assertNull(stack.topItem)
        assertEquals(ConsolePage.Tasks, stack.top?.page)
    }

    @Test
    fun `a sub-item with no item showing has nothing to open over`() {
        val stack = stack { open(ConsolePage.Tasks) }
        val before = stack.toList()
        stack.openSubItem("scan/triggers")
        assertEquals(before, stack.toList())
    }

    @Test
    fun `with no page open there is nowhere to open an item`() {
        val stack = stack()
        stack.openItem("scan")
        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
    }

    @Test
    fun `a page's item opens from another page, over a page of its own, and Back returns one step at a time`() {
        val stack = stack { open(ConsolePage.Activity) }
        stack.openPageItem(ConsolePage.Users, "user:1", ids)
        assertEquals(listOf(ConsolePage.Activity, ConsolePage.Users, ConsolePage.Users), stack.pages())
        assertEquals("user:1", stack.topItem?.item)
        stack.closeTop()
        assertEquals(ConsolePage.Users, stack.top?.page)
        stack.closeTop()
        assertEquals(ConsolePage.Activity, stack.top?.page)
    }

    @Test
    fun `a page opened from the home closes the item its predecessor had open`() {
        val stack = stack { open(ConsolePage.Tasks) }
        stack.openItem("scan")
        stack.openFromHome(ConsolePage.Logs, nextOpening = ids)
        assertNull(stack.topItem)
        assertEquals(listOf(ConsolePage.Logs), stack.pages())
    }

    @Test
    fun `a page opened on a row carries it, and a row on the page already open replaces it`() {
        val stack = stack()
        stack.openPage(ConsolePage.Playback, "audio", ids)
        assertEquals("audio", stack.top?.row)
        val opening = stack.top?.opening
        stack.openPage(ConsolePage.Playback, "subtitles", ids)
        assertEquals("subtitles", stack.top?.row)
        // Same page, same opening: the row changes and the page stays.
        assertEquals(opening, stack.top?.opening)
        assertEquals(1, stack.entries().size)
    }

    @Test
    fun `a row on the page already open under an item is not shown`() {
        val stack = stack { open(ConsolePage.Tasks) }
        stack.openItem("scan")
        stack.openPage(ConsolePage.Tasks, "row", ids)
        assertNull(stack.entries().first().row)
    }

    @Test
    fun `a page opened from the home on a row carries it, and reopening it on another shows that one`() {
        val stack = stack()
        stack.openFromHome(ConsolePage.Playback, "audio", ids)
        val opening = stack.top?.opening
        stack.openFromHome(ConsolePage.Playback, "subtitles", ids)
        assertEquals("subtitles", stack.top?.row)
        assertEquals(opening, stack.top?.opening)
    }
}
