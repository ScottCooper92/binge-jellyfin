package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.ServerIdentity
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.session
import com.cooper.homelab.core.telemetry.NoOpCrashBreadcrumbs
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A session store in memory, so what the planner sees changes when the test says so and at no other time. */
private class MemorySessionStore : SessionStore {
    private val state = MutableStateFlow<JellyfinSession?>(null)
    override val session: Flow<JellyfinSession?> = state

    override suspend fun current(): JellyfinSession? = state.value

    override suspend fun save(session: JellyfinSession): Boolean {
        state.value = session
        return true
    }

    override suspend fun clear() {
        state.value = null
    }

    override suspend fun deviceId(): String = "device"
}

/**
 * The planner runs on the test's own scheduler, over stores that live in memory, so nothing here waits on a real thread or a real
 * clock: each step changes one input and runs everything that follows from it (`runCurrent`, which also runs the background scope's work; `advanceUntilIdle` would skip it) before the next step looks. An earlier version ran
 * the planner on `Dispatchers.IO` over a store in a real file and polled for the calls with a five-second wall-clock limit, which
 * timed out on a slow CI machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationPlannerTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = MemorySessionStore()
    private val prefs = NotificationPrefs(InMemoryDataStore())
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()

    @After
    fun close() = jellyfin.close()

    /** Starts the planner on [this] test's scope and lets it take its first look at the (signed-out) session. */
    private fun TestScope.planner() {
        NotificationPlanner(
            sessions,
            prefs,
            ServerIdentity(InMemoryDataStore()),
            scheduler,
            notifier,
            backgroundScope,
            NoOpCrashBreadcrumbs,
        ).start()
        runCurrent()
    }

    @Test
    fun `it schedules only while signed in with the switch on, at the chosen interval`() =
        runTest {
            planner()
            assertEquals(listOf("cancel"), scheduler.calls)
            scheduler.calls.clear()

            sessions.save(jellyfin.session())
            prefs.setEnabled(true)
            runCurrent()
            assertTrue("schedule 60" in scheduler.calls)

            prefs.setIntervalMinutes(30)
            runCurrent()
            assertEquals("schedule 30", scheduler.calls.last())

            prefs.setEnabled(false)
            runCurrent()
            assertEquals("cancel", scheduler.calls.last())
        }

    @Test
    fun `signing out cancels the schedule and clears the notices`() =
        runTest {
            planner()
            sessions.save(jellyfin.session())
            prefs.setEnabled(true)
            runCurrent()
            assertEquals("schedule 60", scheduler.calls.last())

            sessions.clear()
            runCurrent()
            assertEquals("cancel", scheduler.calls.last())
            assertTrue(notifier.activityCancels > 0)
        }

    @Test
    fun `a pause stops the poll, and signing in again lifts it`() =
        runTest {
            planner()
            sessions.save(jellyfin.session())
            prefs.setEnabled(true)
            runCurrent()
            assertEquals("schedule 60", scheduler.calls.last())

            prefs.setPausedForAuthFailure(true)
            runCurrent()
            assertEquals("cancel", scheduler.calls.last())

            sessions.save(jellyfin.session().copy(token = "token-2"))
            runCurrent()
            assertEquals("schedule 60", scheduler.calls.last())
            assertFalse(prefs.pausedForAuthFailure.first())
            assertTrue(notifier.connectionCancels > 0)
        }
}
