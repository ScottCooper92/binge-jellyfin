package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.auth.ServerIdentity
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.homelab.core.telemetry.NoOpCrashBreadcrumbs
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationPlannerTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val prefs = NotificationPrefs(InMemoryDataStore())
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        NotificationPlanner(sessions, prefs, ServerIdentity(InMemoryDataStore()), scheduler, notifier, scope, NoOpCrashBreadcrumbs).start()
    }

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private suspend fun until(check: () -> Boolean) = withTimeout(5_000) { while (!check()) delay(10) }

    @Test
    fun `it schedules only while signed in with the switch on, at the chosen interval`() =
        runBlocking {
            until { "cancel" in scheduler.calls }
            scheduler.calls.clear()
            sessions.save(jellyfin.session())
            prefs.setEnabled(true)
            until { "schedule 60" in scheduler.calls }
            prefs.setIntervalMinutes(30)
            until { "schedule 30" in scheduler.calls }
            prefs.setEnabled(false)
            until { scheduler.calls.last() == "cancel" }
        }

    @Test
    fun `signing out cancels the schedule and clears the notices`() =
        runBlocking {
            // Let the planner take its first look at the (signed-out) session; the un-pause ignores that first value.
            until { "cancel" in scheduler.calls }
            sessions.save(jellyfin.session())
            prefs.setEnabled(true)
            until { "schedule 60" in scheduler.calls }
            sessions.clear()
            until { scheduler.calls.last() == "cancel" && notifier.activityCancels > 0 }
        }

    @Test
    fun `a pause stops the poll, and signing in again lifts it`() =
        runBlocking {
            // Let the planner take its first look at the (signed-out) session; the un-pause ignores that first value.
            until { "cancel" in scheduler.calls }
            sessions.save(jellyfin.session())
            prefs.setEnabled(true)
            until { "schedule 60" in scheduler.calls }
            prefs.setPausedForAuthFailure(true)
            until { scheduler.calls.last() == "cancel" }
            sessions.save(jellyfin.session().copy(token = "token-2"))
            until { scheduler.calls.last() == "schedule 60" }
            assertFalse(prefs.pausedForAuthFailure.first())
            assertTrue(notifier.connectionCancels > 0)
        }
}
