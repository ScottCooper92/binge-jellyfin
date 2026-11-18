package com.cooper.homelab.ephyra.notifications

import com.cooper.homelab.core.notifications.FeedCursorKey
import com.cooper.homelab.core.testing.InMemoryDataStore
import com.cooper.homelab.ephyra.auth.ServerIdentity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySwitchTest {
    private val prefs = NotificationPrefs(InMemoryDataStore())
    private val cursors = testFeedCursors()
    private val serverIdentity = ServerIdentity(InMemoryDataStore())
    private val switch = ActivitySwitch(prefs, cursors, serverIdentity)

    @Test
    fun `turning it on drops the cursor so the backlog is not announced and turning it off keeps it`() =
        runBlocking {
            val key = FeedCursorKey(serverIdentity.currentServerId(), "user-1", ACTIVITY_SIGNAL)
            cursors.set(key, "7")

            switch.set(false)
            assertEquals("7", cursors.get(key))
            assertFalse(prefs.enabled.first())

            switch.set(true)
            assertNull(cursors.get(key))
            assertTrue(prefs.enabled.first())
        }
}
