package com.cooper.binge.companion.jellyfin

import com.cooper.homelab.core.testing.FakeTipBilling
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TipSettlingTest {
    @Test
    fun `starting the app settles the tips once`() {
        val billing = FakeTipBilling(prices = null)
        val scope = TestScope(UnconfinedTestDispatcher())

        settleTipsAtStart(billing, scope)

        assertEquals(1, billing.settled)
    }

    @Test
    fun `settling does not wait on the caller, so the start is not held up`() {
        val billing = FakeTipBilling(prices = null)
        val scope = TestScope()

        settleTipsAtStart(billing, scope)
        // The standard dispatcher has not run it yet: it runs on the scope, off the caller's path.
        assertEquals(0, billing.settled)
        scope.testScheduler.advanceUntilIdle()

        assertEquals(1, billing.settled)
    }
}
