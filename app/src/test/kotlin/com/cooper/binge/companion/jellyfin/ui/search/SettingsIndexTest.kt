package com.cooper.binge.companion.jellyfin.ui.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsIndexTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun find(
        query: String,
        available: (ConsolePage) -> Boolean = { true },
    ) = searchSettings(query, settingsIndex, available, context::getString).map { context.getString(it.label) }

    /** A page the search cannot reach is a page nobody finds; these are the ones that are not a place of their own. */
    @Test
    fun `every page but the account, the search and recording has an entry of its own`() {
        val left = setOf(ConsolePage.Account, ConsolePage.Search, ConsolePage.LiveTvRecording)
        val indexed = settingsIndex.filter { it.pageLabel == null }.map { it.page }.toSet()
        assertEquals(emptyList<ConsolePage>(), ConsolePage.entries.filter { it !in left && it !in indexed })
    }

    @Test
    fun `every entry has its words`() {
        settingsIndex.forEach {
            assertTrue(context.getString(it.label).isNotBlank())
            it.keywords?.let { k -> assertTrue(context.getString(k).isNotBlank()) }
        }
    }

    @Test
    fun `a setting is found by its name, best match first`() {
        assertEquals("HTTP port", find("http port").first())
        assertEquals("Hardware acceleration", find("hardware").first())
    }

    @Test
    fun `a setting is found by a word that is not in its name`() {
        assertTrue("Hardware acceleration" in find("gpu"))
        assertTrue("Change password" in find("security"))
    }

    @Test
    fun `every word of the query has to match`() {
        assertEquals(emptyList<String>(), find("hardware zzz"))
        assertEquals(emptyList<String>(), find("   "))
    }

    @Test
    fun `case and accents do not matter`() {
        assertEquals(find("HARDWARE"), find("hardware"))
        assertEquals("cafe", "Café".searchForm())
    }

    @Test
    fun `a page the user may not open is never offered`() {
        val user: (ConsolePage) -> Boolean = { it == ConsolePage.Password || it == ConsolePage.ThisApp }
        assertEquals(emptyList<String>(), find("hardware", user))
        assertTrue("Change password" in find("password", user))
    }

    @Test
    fun `a page comes before a setting that matches as well`() {
        assertEquals(context.getString(R.string.network_title), find("network").first())
    }
}
