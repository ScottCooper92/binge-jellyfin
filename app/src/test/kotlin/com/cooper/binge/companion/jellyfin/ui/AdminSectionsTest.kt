package com.cooper.binge.companion.jellyfin.ui

import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the home sections an administrator's pages: the places Jellyfin's own dashboard puts them, the commonest first. */
class AdminSectionsTest {
    private val pages = adminSectionPages()

    @Test
    fun `every administrator page is in exactly one section, so none is left off the home or listed twice`() {
        assertEquals(pages.size, pages.toSet().size)
        assertEquals(ConsolePage.entries.filter { it.isAdmin }.toSet(), pages.toSet())
    }

    @Test
    fun `the sections follow the web dashboard, Server then Devices then Advanced`() {
        val server = listOf(ConsolePage.Users, ConsolePage.Libraries, ConsolePage.Transcoding, ConsolePage.ServerSettings)
        val devices = listOf(ConsolePage.Devices, ConsolePage.Activity)
        val advanced = listOf(ConsolePage.Tasks, ConsolePage.Logs, ConsolePage.Network, ConsolePage.ApiKeys, ConsolePage.Server)
        assertTrue(server.all { pages.indexOf(it) < pages.indexOf(ConsolePage.Devices) })
        assertTrue(devices.all { pages.indexOf(it) < pages.indexOf(ConsolePage.Tasks) })
        assertTrue(advanced.all { pages.indexOf(it) > pages.indexOf(ConsolePage.Activity) })
    }

    @Test
    fun `the pages opened most come first in their section`() {
        assertTrue(pages.indexOf(ConsolePage.Users) < pages.indexOf(ConsolePage.Libraries))
        assertTrue(pages.indexOf(ConsolePage.Libraries) < pages.indexOf(ConsolePage.Transcoding))
        assertTrue(pages.indexOf(ConsolePage.Transcoding) < pages.indexOf(ConsolePage.MetadataDefaults))
        assertTrue(pages.indexOf(ConsolePage.Tasks) < pages.indexOf(ConsolePage.Logs))
    }
}
