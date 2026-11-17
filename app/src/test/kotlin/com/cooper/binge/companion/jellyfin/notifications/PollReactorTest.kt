package com.cooper.binge.companion.jellyfin.notifications

import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.notifications.CheckResult
import com.cooper.homelab.core.notifications.PollOutcome
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PollReactorTest {
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private val prefs = NotificationPrefs(InMemoryDataStore())
    private val reactor = pollReactor(notifier, scheduler, prefs) { false }

    @Test
    fun `an ordinary check succeeds and a transient failure retries`() =
        runBlocking {
            assertEquals(PollOutcome.Succeed, reactor.react(ServerId("s"), CheckResult.Ok))
            assertEquals(PollOutcome.Retry, reactor.react(ServerId("s"), CheckResult.TransientFailure))
            assertTrue(scheduler.calls.isEmpty())
        }

    @Test
    fun `a refused sign-in tells the user, stops the poll and pauses it`() =
        runBlocking {
            assertEquals(PollOutcome.Succeed, reactor.react(ServerId("s"), CheckResult.AuthFailure))
            assertEquals(1, notifier.connectionProblems)
            assertEquals(listOf("cancel"), scheduler.calls)
            assertTrue(prefs.pausedForAuthFailure.first())
        }

    @Test
    fun `a user who stopped being an administrator has the switch turned off`() =
        runBlocking {
            prefs.setEnabled(true)
            assertEquals(PollOutcome.Succeed, reactor.react(ServerId("s"), CheckResult.StopPolling))
            assertEquals(listOf("cancel"), scheduler.calls)
            assertFalse(prefs.enabled.first())
        }
}
