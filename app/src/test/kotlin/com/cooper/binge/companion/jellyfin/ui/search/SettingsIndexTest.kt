package com.cooper.binge.companion.jellyfin.ui.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.presentation.search.searchSettings
import com.cooper.homelab.core.uiandroid.resolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsIndexTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val index = settingsIndex(context.resources)

    private fun find(
        query: String,
        available: (SettingEntry<SearchTarget>) -> Boolean = { true },
    ) = searchSettings(query, index, available) { it.resolve(context) }.map { it.label.resolve(context) }

    /** A page the search cannot reach is a page nobody finds; these are the ones that are not a place of their own. */
    @Test
    fun `every page but the account, the search and recording has an entry of its own`() {
        val left = setOf(ConsolePage.Account, ConsolePage.Search, ConsolePage.LiveTvRecording)
        val indexed = index.filter { it.pageLabel == null }.map { it.target.page }.toSet()
        assertEquals(emptyList<ConsolePage>(), ConsolePage.entries.filter { it !in left && it !in indexed })
    }

    @Test
    fun `every switch the search can carry has an entry that names its row`() {
        val rows = index.mapNotNull { it.row }.toSet()
        assertEquals(emptySet<String>(), SWITCH_SPECS.keys - rows)
    }

    @Test
    fun `every entry has its words`() {
        index.forEach {
            assertTrue(it.label.resolve(context).isNotBlank())
            it.keywords?.let { k -> assertTrue(k.resolve(context).isNotBlank()) }
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
    }

    @Test
    fun `a page the user may not open is never offered`() {
        val user: (SettingEntry<SearchTarget>) -> Boolean = {
            it.target.page == ConsolePage.Password ||
                it.target.page == ConsolePage.ThisApp
        }
        assertEquals(emptyList<String>(), find("hardware", user))
        assertTrue("Change password" in find("password", user))
    }

    @Test
    fun `a page comes before a setting that matches as well`() {
        assertEquals(context.getString(R.string.network_title), find("network").first())
    }

    @Test
    fun `a setting its page hides on this server is not offered`() {
        val admin = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":true}""").jsonObject)

        fun offered(version: String) = find("av1") { it.available(JellyfinServerProfile.from(version), admin) }
        assertTrue("Allow AV1 encoding" in offered("10.11.6"))
        assertFalse("Allow AV1 encoding" in offered("10.8.13"))
    }

    @Test
    fun `the alerts row is offered to an administrator only`() {
        val profile = JellyfinServerProfile.from("10.11.6")

        fun offered(admin: Boolean) =
            find("battery") { it.available(profile, account(admin)) } + find("server alerts") { it.available(profile, account(admin)) }
        assertTrue("Server alerts" in offered(admin = true))
        assertEquals(emptyList<String>(), offered(admin = false))
    }

    @Test
    fun `an entry that names a row names it once on its page`() {
        val named = index.filter { it.row != null }
        assertTrue(named.isNotEmpty())
        assertEquals(named.map { it.target.page to it.row }.distinct().size, named.size)
        named.forEach { assertTrue(it.row!!.isNotBlank()) }
    }

    private fun account(admin: Boolean) = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":$admin}""").jsonObject)
}
