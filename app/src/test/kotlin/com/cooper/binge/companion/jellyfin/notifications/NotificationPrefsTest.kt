package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class NotificationPrefsTest {
    private val store = InMemoryDataStore()
    private val prefs = NotificationPrefs(store)

    @Test
    fun `it is off, at the default interval, until the user says otherwise`() =
        runBlocking {
            assertFalse(prefs.enabled.first())
            assertEquals(DEFAULT_POLL_INTERVAL_MINUTES, prefs.intervalMinutes.first())
        }

    @Test
    fun `an interval is one of the choices`() {
        runBlocking {
            POLL_INTERVAL_CHOICES.forEach {
                prefs.setIntervalMinutes(it)
                assertEquals(it, prefs.intervalMinutes.first())
            }
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { prefs.setIntervalMinutes(7) } }
    }

    @Test
    fun `the released cursor belongs to one account, and turning the switch on drops it`() =
        runBlocking {
            store.writeReleasedCursor("a/b", id = 4, dateMillis = 1000L)
            assertEquals(ActivityCursor(4, 1000L), prefs.cursor("a/b"))
            assertNull(prefs.cursor("a/c"))
            prefs.setEnabled(false)
            assertEquals(ActivityCursor(4, 1000L), prefs.cursor("a/b"))
            prefs.setEnabled(true)
            assertNull(prefs.cursor("a/b"))
        }

    @Test
    fun `a released cursor without a date reads back without one`() =
        runBlocking {
            store.writeReleasedCursor("a/b", id = 5, dateMillis = null)
            assertEquals(ActivityCursor(5, null), prefs.cursor("a/b"))
        }
}
