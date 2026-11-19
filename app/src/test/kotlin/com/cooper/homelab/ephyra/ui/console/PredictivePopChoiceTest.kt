package com.cooper.homelab.ephyra.ui.console

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which predictive-back drag a popped console entry gets: read from its metadata, because an entry's key is not public. */
class PredictivePopChoiceTest {
    private val covering = mapOf<String, Any>(COVERS_WINDOW to true)
    private val ordinary = mapOf<String, Any>("some" to "metadata")

    @Test
    fun `a page beside the home is a drill-down`() = assertTrue(isDrillDownPop(ordinary, hubBeside = true))

    @Test
    fun `a page that covers the window is a whole screen even beside the home`() = assertFalse(isDrillDownPop(covering, hubBeside = true))

    @Test
    fun `a page on a window with no home beside it is a whole screen`() {
        assertFalse(isDrillDownPop(ordinary, hubBeside = false))
        assertFalse(isDrillDownPop(covering, hubBeside = false))
    }

    @Test
    fun `an entry with no metadata is not a covering page`() = assertTrue(isDrillDownPop(null, hubBeside = true))

    @Test
    fun `the search page is marked as covering the window and an ordinary page is not`() {
        assertTrue(ConsolePage.entries.any { it.coversWindow })
        ConsolePage.entries.forEach { page ->
            assertTrue(pageMetadata(page).containsKey(COVERS_WINDOW) == page.coversWindow)
        }
    }
}
