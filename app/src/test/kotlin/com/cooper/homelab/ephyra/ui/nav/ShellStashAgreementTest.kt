package com.cooper.homelab.ephyra.ui.nav

import com.cooper.homelab.ephyra.ui.console.ConsolePage
import com.cooper.homelab.ephyra.ui.console.PAGE_ROLE
import com.cooper.homelab.ephyra.ui.console.pageMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared stash parks what [opensBesideHome] names, and the scene draws what carries the page marker. The two must name the same
 * pages, or a page would be parked that the scene does not draw beside the home, or the reverse.
 */
class ShellStashAgreementTest {
    @Test
    fun `every page that opens beside the home carries the pane marker, and no other does`() {
        ConsolePage.entries.forEach { page ->
            assertEquals(
                "$page",
                opensBesideHome(PageRoute(page, 1)),
                pageMetadata(page)[PAGE_ROLE] != null,
            )
        }
    }
}
