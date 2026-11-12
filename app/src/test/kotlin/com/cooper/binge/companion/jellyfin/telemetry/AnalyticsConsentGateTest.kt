package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only because a throwing hook is logged through `android.util.Log`. */
@RunWith(RobolectricTestRunner::class)
class AnalyticsConsentGateTest {
    private fun TestScope.gate(prefs: TelemetryPrefs): AnalyticsConsentGate =
        AnalyticsConsentGate(prefs, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `the gate stays shut while undecided and after a decline, without telling a backend to close`() =
        runTest {
            val prefs = TelemetryPrefs(InMemoryDataStore())
            val gate = gate(prefs)
            val seen = mutableListOf<Boolean>()
            gate.register { seen += it }
            assertFalse(gate.isGranted)
            assertFalse(gate.granted.value)

            prefs.setAnalyticsGranted(false)
            assertFalse(gate.isGranted)
            assertEquals(emptyList<Boolean>(), seen)
        }

    @Test
    fun `a grant opens every backend before granted turns true, and a later decline closes them`() =
        runTest {
            val prefs = TelemetryPrefs(InMemoryDataStore())
            val gate = gate(prefs)
            val seen = mutableListOf<Pair<Boolean, Boolean>>()
            gate.register { seen += it to gate.granted.value }

            prefs.setAnalyticsGranted(true)
            assertTrue(gate.isGranted)
            assertTrue(gate.granted.value)
            assertEquals("the hook ran while granted was still false", listOf(true to false), seen)

            prefs.setAnalyticsGranted(false)
            assertFalse(gate.isGranted)
            assertFalse(gate.granted.value)
            assertEquals(listOf(true to false, false to true), seen)
        }

    @Test
    fun `a backend registering after the grant is opened at once`() =
        runTest {
            val prefs = TelemetryPrefs(InMemoryDataStore())
            prefs.setAnalyticsGranted(true)
            val gate = gate(prefs)

            val seen = mutableListOf<Boolean>()
            gate.register { seen += it }
            assertEquals(listOf(true), seen)
        }

    @Test
    fun `a throwing backend is skipped and does not strand a caller awaiting the grant`() =
        runTest {
            val prefs = TelemetryPrefs(InMemoryDataStore())
            val gate = gate(prefs)
            val seen = mutableListOf<Boolean>()
            gate.register { error("SDK refused") }
            gate.register { seen += it }
            var released = false
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                gate.granted.first { it }
                released = true
            }
            assertFalse(released)

            prefs.setAnalyticsGranted(true)
            testScheduler.runCurrent()
            assertEquals(listOf(true), seen)
            assertTrue(released)
        }

    @Test
    fun `awaitRead returns once the stored answer is read, whichever it is`() =
        runTest {
            val gate = gate(TelemetryPrefs(InMemoryDataStore()))
            var read = false
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                gate.awaitRead()
                read = true
            }
            testScheduler.runCurrent()
            assertTrue(read)
            assertFalse(gate.isGranted)
        }
}
