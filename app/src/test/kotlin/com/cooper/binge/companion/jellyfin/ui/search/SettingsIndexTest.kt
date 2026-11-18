package com.cooper.binge.companion.jellyfin.ui.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.ENCODING_ACCELERATION
import com.cooper.binge.companion.jellyfin.server.ENCODING_ENHANCED_NVDEC
import com.cooper.binge.companion.jellyfin.server.ENCODING_HEVC
import com.cooper.binge.companion.jellyfin.server.ENCODING_SYSTEM_NATIVE
import com.cooper.binge.companion.jellyfin.server.ENCODING_THROTTLING
import com.cooper.binge.companion.jellyfin.server.ENCODING_VPP_TONEMAPPING
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.presentation.search.searchSettings
import com.cooper.homelab.core.uiandroid.resolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
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
    fun `the cache size and legacy authorization are offered only where the server has them`() {
        val admin = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":true}""").jsonObject)

        fun offered(
            query: String,
            version: String,
        ) = find(query) { it.available(JellyfinServerProfile.from(version), admin) }
        listOf("Cache size", "Legacy authorization").forEach {
            assertTrue(it in offered(it, "10.11.6"))
            assertFalse(it in offered(it, "10.10.7"))
        }
    }

    private fun encoding(
        type: String,
        vararg keys: String,
    ) = buildJsonObject {
        put(ENCODING_ACCELERATION, type)
        keys.forEach { put(it, true) }
    }

    private fun drawn(
        key: String,
        encoding: JsonObject?,
    ) = index.first { it.row == SettingRows.transcoding(key) }.drawnFor(encoding, JellyfinServerProfile.from("10.11.6"))

    @Test
    fun `a transcoding entry is offered only for the acceleration types whose page draws its row`() {
        val every =
            arrayOf(ENCODING_ENHANCED_NVDEC, ENCODING_SYSTEM_NATIVE, ENCODING_VPP_TONEMAPPING, ENCODING_HEVC, ENCODING_THROTTLING)
        val qsv = encoding("qsv", *every)
        assertTrue(drawn(ENCODING_SYSTEM_NATIVE, qsv) && drawn(ENCODING_VPP_TONEMAPPING, qsv) && drawn(ENCODING_HEVC, qsv))
        assertFalse(drawn(ENCODING_ENHANCED_NVDEC, qsv))
        val nvenc = encoding("nvenc", *every)
        assertTrue(drawn(ENCODING_ENHANCED_NVDEC, nvenc))
        assertFalse(drawn(ENCODING_SYSTEM_NATIVE, nvenc) || drawn(ENCODING_VPP_TONEMAPPING, nvenc))
        // With no acceleration the page draws no hardware rows, and still draws the ones that are for every type.
        val none = encoding("none", *every)
        assertFalse(drawn(ENCODING_HEVC, none))
        assertTrue(drawn(ENCODING_THROTTLING, none))
    }

    @Test
    fun `a transcoding entry is not offered where the server did not send its key`() {
        assertFalse(drawn(ENCODING_THROTTLING, encoding("qsv")))
        assertTrue(drawn(ENCODING_THROTTLING, encoding("qsv", ENCODING_THROTTLING)))
    }

    @Test
    fun `every entry is offered when the encoding configuration could not be read`() {
        val profile = JellyfinServerProfile.from("10.11.6")
        assertEquals(emptyList<String>(), index.filterNot { it.drawnFor(null, profile) }.map { it.id })
    }

    @Test
    fun `an entry that is no transcoding switch is not changed by the configuration`() {
        val profile = JellyfinServerProfile.from("10.11.6")
        val nothing = encoding("none")
        val others = index.filter { it.row == null || it.row !in ENCODING_SPECS }
        assertEquals(emptyList<String>(), others.filterNot { it.drawnFor(nothing, profile) }.map { it.id })
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
