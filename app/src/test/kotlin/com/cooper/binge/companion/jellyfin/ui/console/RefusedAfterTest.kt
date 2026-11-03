package com.cooper.binge.companion.jellyfin.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one rule every save-as-made page reads its `refused` through. */
class RefusedAfterTest {
    @Test
    fun `a change clears a refusal`() {
        assertNull(refusedAfter(ConsoleFailure.Refused, null))
    }

    @Test
    fun `a refusal is said, with the server's reason if it gave one`() {
        assertEquals(ConsoleFailure.Refused, refusedAfter(null, ConsoleFailure.Refused))
        val reason = ConsoleFailure.refused("Not enough free space")
        assertEquals("Not enough free space", refusedAfter(null, reason)?.reason)
    }

    @Test
    fun `a write that could not reach the server leaves what was said, so another save's refusal stays`() {
        assertEquals(ConsoleFailure.Refused, refusedAfter(ConsoleFailure.Refused, ConsoleFailure.Unreachable))
        assertNull(refusedAfter(null, ConsoleFailure.Unreachable))
    }
}
