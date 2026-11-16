package com.cooper.binge.companion.jellyfin.notifications

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
    private val reactor = PollReactor(notifier, scheduler, prefs)

    @Test
    fun `an ordinary check succeeds and a transient failure retries`() =
        runBlocking {
            assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.Ok))
            assertEquals(PollOutcome.Retry, reactor.react(CheckResult.TransientFailure))
            assertTrue(scheduler.calls.isEmpty())
        }

    @Test
    fun `a refused sign-in tells the user, stops the poll and pauses it`() =
        runBlocking {
            assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.AuthFailure))
            assertEquals(1, notifier.connectionProblems)
            assertEquals(listOf("cancel"), scheduler.calls)
            assertTrue(prefs.pausedForAuthFailure.first())
        }

    @Test
    fun `a user who stopped being an administrator has the switch turned off`() =
        runBlocking {
            prefs.setEnabled(true)
            assertEquals(PollOutcome.Succeed, reactor.react(CheckResult.NotAdministrator))
            assertEquals(listOf("cancel"), scheduler.calls)
            assertFalse(prefs.enabled.first())
        }
}
