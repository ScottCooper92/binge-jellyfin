package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConsoleBackStackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun ConsoleBackStack.pages() = entries.map { it.page }

    @Test
    fun `a page opened from another returns to it on Back, and then to the home`() {
        val plugins = ConsoleBackStack().open(ConsolePage.Plugins)
        val server = plugins.open(ConsolePage.Server)
        assertEquals(ConsolePage.Server, server.top?.page)
        val back = server.back()
        assertEquals(ConsolePage.Plugins, back.top?.page)
        // It is the same opening, so its view models are the ones it had.
        assertEquals(plugins.top, back.top)
        assertNull(back.back().top)
        assertNull(ConsoleBackStack().back().top)
    }

    @Test
    fun `opening the page already on top does nothing, and every opening has its own number`() {
        val once = ConsoleBackStack().open(ConsolePage.Server)
        assertSame(once, once.open(ConsolePage.Server))
        val reopened = once.back().open(ConsolePage.Server)
        // The same page opened again is a new opening, so it never gets the old one's view models.
        assertEquals(setOf(1), reopened.openings)
        assertEquals(setOf(0, 1), once.open(ConsolePage.Plugins).openings + once.openings + reopened.openings)
    }

    @Test
    fun `signing out closes every page`() {
        val stack = ConsoleBackStack().open(ConsolePage.Plugins).open(ConsolePage.Server).clear()
        assertNull(stack.top)
        assertEquals(emptySet<Int>(), stack.openings)
    }

    @Test
    fun `a page the server or the user lost leaves the stack, wherever it is, and Back skips it`() {
        val stack = ConsoleBackStack().open(ConsolePage.ApproveCode).open(ConsolePage.Plugins).open(ConsolePage.Server)
        val kept = stack.keeping { it != ConsolePage.Plugins }
        assertEquals(listOf(ConsolePage.ApproveCode, ConsolePage.Server), kept.pages())
        assertEquals(ConsolePage.ApproveCode, kept.back().top?.page)
        // Nothing lost: the very same stack comes back.
        assertSame(stack, stack.keeping { true })
    }

    @Test
    fun `the stack survives the Activity being recreated, openings and all`() {
        var stack = ConsoleBackStack()
        var change: (ConsoleBackStack) -> Unit = {}
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            var saved by rememberSaveable(stateSaver = ConsoleBackStack.Saver) { mutableStateOf(ConsoleBackStack()) }
            stack = saved
            change = { saved = it }
        }
        compose.setContent(content)
        compose.runOnIdle { change(ConsoleBackStack().open(ConsolePage.Plugins).open(ConsolePage.Server)) }
        compose.waitForIdle()
        // Rotation and process death both recreate the Activity, which gets back what it saved and nothing else.
        compose.scenario.recreate()
        compose.setContent(content)
        compose.waitForIdle()
        assertEquals(listOf(ConsolePage.Plugins, ConsolePage.Server), stack.pages())
        assertEquals(setOf(0, 1), stack.openings)
        // A page opened after the restore doesn't reuse a number the stack already gave out.
        assertEquals(setOf(0, 1, 2), stack.open(ConsolePage.Logs).openings)
    }

    @Test
    fun `a saved stack that can't be read is dropped rather than half restored`() {
        with(ConsoleBackStack.Saver) {
            assertNull(restore(emptyList()))
            assertNull(restore(listOf(1, "NoSuchPage", 0, "")))
            assertNull(restore(listOf(1, "Server", "zero", "")))
            assertNull(restore(listOf(1, "Server", 0)))
        }
    }

    @Test
    fun `a page opened from the home beside a page replaces it and what it opened`() {
        val stack = ConsoleBackStack().open(ConsolePage.Plugins).open(ConsolePage.Server)
        val logs = stack.openFromHome(ConsolePage.Logs)
        assertEquals(listOf(ConsolePage.Logs), logs.pages())
        // Back from it is the home, not Plugins or Server again.
        assertEquals(emptyList<ConsolePage>(), logs.back().pages())
        // A number is not reused, so the new page gets view models of its own.
        assertEquals(setOf(2), logs.openings)
    }

    @Test
    fun `the page already open at the bottom keeps its opening when the home opens it again`() {
        val stack = ConsoleBackStack().open(ConsolePage.Plugins).open(ConsolePage.Server)
        val again = stack.openFromHome(ConsolePage.Plugins)
        assertEquals(listOf(ConsolePage.Plugins), again.pages())
        assertEquals(setOf(0), again.openings)
    }

    @Test
    fun `the home is the hub once signed in, and a page keeps its key either way`() {
        val stack = ConsoleBackStack().open(ConsolePage.Logs)
        assertEquals(ConsoleKey.Home, stack.keys(signedIn = false).first())
        assertEquals(ConsoleKey.Hub, stack.keys(signedIn = true).first())
        assertEquals(stack.keys(false).drop(1), stack.keys(true).drop(1))
    }

    @Test
    fun `an item opens above its page under the same opening, and Back returns to the page`() {
        val stack = ConsoleBackStack().open(ConsolePage.Tasks).openItem("scan")
        assertEquals(listOf(ConsolePage.Tasks, ConsolePage.Tasks), stack.pages())
        assertEquals(setOf(0), stack.openings)
        assertEquals("scan", stack.topItem?.item)
        assertEquals(
            listOf(ConsoleKey.Hub, ConsoleKey.Page(ConsolePage.Tasks, 0), ConsoleKey.Item(ConsolePage.Tasks, 0, "scan")),
            stack.keys(signedIn = true),
        )
        assertNull(stack.back().topItem)
        assertEquals(listOf(ConsolePage.Tasks), stack.back().pages())
    }

    @Test
    fun `opening another item replaces the one open, and opening the one open does nothing`() {
        val stack = ConsoleBackStack().open(ConsolePage.Tasks).openItem("scan")
        assertSame(stack, stack.openItem("scan"))
        val other = stack.openItem("clean")
        assertEquals(listOf(null, "clean"), other.entries.map { it.item })
        assertEquals(listOf(ConsolePage.Tasks), other.back().pages())
    }

    @Test
    fun `with no page open there is nowhere to open an item`() {
        val empty = ConsoleBackStack()
        assertSame(empty, empty.openItem("scan"))
    }

    @Test
    fun `a page opened from the home closes the item its predecessor had open`() {
        val stack = ConsoleBackStack().open(ConsolePage.Tasks).openItem("scan").openFromHome(ConsolePage.Logs)
        assertEquals(listOf(ConsolePage.Logs), stack.pages())
        assertNull(stack.topItem)
    }

    @Test
    fun `an item survives the save and restore`() {
        val stack = ConsoleBackStack().open(ConsolePage.Tasks).openItem("scan").open(ConsolePage.Server)
        with(ConsoleBackStack.Saver) {
            val saved = SaverScope { true }.save(stack)
            assertEquals(stack, restore(checkNotNull(saved)))
        }
    }
}
