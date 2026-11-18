package com.cooper.homelab.ephyra.notifications

import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Which scheduling failures say something to fix here, and what is recorded for each. */
class PollFailuresTest {
    private class Recording : CrashBreadcrumbs {
        val logs = mutableListOf<String>()
        val nonFatals = mutableListOf<Pair<String, Throwable>>()

        override fun log(message: String) {
            logs += message
        }

        override fun key(
            name: String,
            value: String,
        ) = Unit

        override fun nonFatal(
            message: String,
            cause: Throwable,
        ) {
            nonFatals += message to cause
        }
    }

    private class OurOwnTrouble : Exception()

    @Test
    fun `an invalid work request or constraint is ours`() {
        assertEquals(PollFailure.Ours, classifyPollFailure(IllegalArgumentException("a period below the minimum")))
    }

    @Test
    fun `a misused or uninitialised WorkManager is ours`() {
        assertEquals(PollFailure.Ours, classifyPollFailure(IllegalStateException("WorkManager is not initialized properly")))
    }

    @Test
    fun `an exception of this app's own classes is ours`() {
        assertEquals(PollFailure.Ours, classifyPollFailure(OurOwnTrouble()))
    }

    @Test
    fun `the job limit is the platform's whichever way it is worded`() {
        assertEquals(
            PollFailure.Environment,
            classifyPollFailure(IllegalStateException("JobScheduler 100 job limit exceeded. We count 100")),
        )
        assertEquals(
            PollFailure.Environment,
            classifyPollFailure(IllegalStateException("Apps may not schedule more than 100 distinct jobs")),
        )
    }

    @Test
    fun `a refusal by the device is not ours`() {
        assertEquals(PollFailure.Environment, classifyPollFailure(SecurityException("background work refused")))
        assertEquals(PollFailure.Environment, classifyPollFailure(IOException("no space left on device")))
        assertEquals(PollFailure.Environment, classifyPollFailure(RuntimeException("something nobody thought of")))
    }

    @Test
    fun `the cause decides when a wrapper hides it`() {
        assertEquals(PollFailure.Ours, classifyPollFailure(RuntimeException("outer", IllegalArgumentException("bad constraint"))))
        assertEquals(
            PollFailure.Environment,
            classifyPollFailure(RuntimeException("outer", IllegalStateException("Apps may not schedule more than 100 distinct jobs"))),
        )
    }

    @Test
    fun `a cancellation anywhere in the chain is not a failure`() {
        assertEquals(PollFailure.Cancelled, classifyPollFailure(CancellationException("stopped")))
        assertEquals(PollFailure.Cancelled, classifyPollFailure(RuntimeException("outer", CancellationException("stopped"))))
    }

    @Test
    fun `a cause that loops back on itself ends`() {
        val failure = RuntimeException("loop")
        val other = RuntimeException("other", failure)
        failure.initCause(other)

        assertEquals(PollFailure.Environment, classifyPollFailure(failure))
    }

    @Test
    fun `a failure of ours is recorded as a non-fatal`() {
        val recording = Recording()
        val failure = IllegalArgumentException("a period below the minimum")

        PollBreadcrumbs(recording).nonFatal("notification poll scheduling failed", failure)

        assertEquals("notification poll scheduling failed", recording.nonFatals.single().first)
        assertTrue(recording.logs.isEmpty())
    }

    @Test
    fun `a refusal by the device records nothing`() {
        val recording = Recording()

        PollBreadcrumbs(recording).nonFatal("notification poll scheduling failed", SecurityException("refused"))

        assertTrue(recording.nonFatals.isEmpty())
        assertTrue(recording.logs.isEmpty())
    }

    @Test
    fun `a cancellation records nothing`() {
        val recording = Recording()

        PollBreadcrumbs(recording).nonFatal("notification poll scheduling failed", CancellationException("stopped"))

        assertTrue(recording.nonFatals.isEmpty())
        assertTrue(recording.logs.isEmpty())
    }

    @Test
    fun `what is reported carries the frames and the class and never the message or the cause`() {
        val recording = Recording()
        val original =
            IllegalArgumentException("http://jelly.home.example:8096 with a token", RuntimeException("the cause says it too"))

        PollBreadcrumbs(recording).nonFatal("notification poll scheduling failed", original)

        val reported = recording.nonFatals.single().second
        assertTrue(reported is PollSchedulingFailed)
        assertEquals("java.lang.IllegalArgumentException", reported.message)
        assertNull(reported.cause)
        assertTrue(reported.stackTrace.contentEquals(original.stackTrace))
        assertTrue(reported.stackTraceToString(), !reported.stackTraceToString().contains("jelly.home.example"))
    }

    @Test
    fun `breadcrumbs and keys still pass through`() {
        val recording = Recording()

        PollBreadcrumbs(recording).log("polling")

        assertEquals(listOf("polling"), recording.logs)
    }
}
