package com.cooper.homelab.ephyra.ui.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.ephyra.ui.Actions
import com.cooper.homelab.ephyra.ui.AppSettings
import com.cooper.homelab.ephyra.ui.NotificationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchTogglesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val index = settingsIndex(context.resources)

    private fun entry(row: String): SettingEntry<SearchTarget> = index.single { it.row == row }

    @Test
    fun `usage data carries the page's value and setter`() {
        val set = mutableListOf<Boolean>()
        val actions = Actions(appSettings = AppSettings(shareUsageData = true), onShareUsageData = { set += it })

        val toggle = searchToggle(entry(SettingRows.THIS_APP_USAGE_DATA), actions)

        assertNotNull(toggle)
        assertTrue(toggle!!.checked)
        assertTrue(toggle.enabled)
        assertFalse(toggle.loading)
        toggle.onCheckedChange(false)
        assertEquals(listOf(false), set)
    }

    @Test
    fun `crash reports carry the page's value and setter, and not the usage data's`() {
        val usage = mutableListOf<Boolean>()
        val crash = mutableListOf<Boolean>()
        val actions =
            Actions(
                appSettings = AppSettings(shareUsageData = true, sendCrashReports = false),
                onShareUsageData = { usage += it },
                onSendCrashReports = { crash += it },
            )

        val toggle = searchToggle(entry(SettingRows.THIS_APP_CRASH_REPORTS), actions)!!

        assertFalse(toggle.checked)
        toggle.onCheckedChange(true)
        assertEquals(listOf(true), crash)
        assertEquals(emptyList<Boolean>(), usage)
    }

    @Test
    fun `notifications carry the page's value and setter for an administrator`() {
        val set = mutableListOf<Boolean>()
        val actions = Actions(notifications = NotificationSettings(enabled = true), onNotificationsEnabled = { set += it })

        val toggle = searchToggle(entry(SettingRows.THIS_APP_NOTIFICATIONS), actions)!!

        assertTrue(toggle.checked)
        toggle.onCheckedChange(false)
        assertEquals(listOf(false), set)
    }

    @Test
    fun `notifications carry no switch when the account has no notification group`() {
        assertNull(searchToggle(entry(SettingRows.THIS_APP_NOTIFICATIONS), Actions(notifications = null)))
    }

    @Test
    fun `a setting that is not a plain switch carries none`() {
        val actions = Actions()
        assertNull(searchToggle(entry(SettingRows.THIS_APP_REPORT_BUG), actions))
        // An entry with no row (a page, or a setting whose row is not keyed) has none.
        assertTrue(index.filter { it.row == null }.all { searchToggle(it, actions) == null })
    }

    /** The entries that carry a switch are real entries of the index, so the two cannot drift apart. */
    @Test
    fun `every switch row is an entry of the index`() {
        val rows = setOf(SettingRows.THIS_APP_USAGE_DATA, SettingRows.THIS_APP_CRASH_REPORTS, SettingRows.THIS_APP_NOTIFICATIONS)
        assertEquals(rows, index.mapNotNull { it.row }.filter { it in rows }.toSet())
    }
}
