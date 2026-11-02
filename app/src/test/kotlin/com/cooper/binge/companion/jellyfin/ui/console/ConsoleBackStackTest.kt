package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    val compose = createAndroidComposeRule<ComponentActivity>()

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
        compose.activityRule.scenario.recreate()
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
            assertNull(restore(listOf(1, "NoSuchPage", 0)))
            assertNull(restore(listOf(1, "Server", "zero")))
        }
    }
}
