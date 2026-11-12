package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingSwitchTest {
    private val prefs = TelemetryPrefs(InMemoryDataStore())
    private val seen = mutableListOf<Boolean>()

    private fun TestScope.start(isDebugBuild: Boolean) =
        CrashReportingSwitch(
            collection = { seen += it },
            prefs = prefs,
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            isDebugBuild = isDebugBuild,
        ).start()

    @Test
    fun `a release build collects nothing until the user turns it on, and stops when they turn it off`() =
        runTest {
            start(isDebugBuild = false)
            prefs.setCrashReportingEnabled(true)
            prefs.setCrashReportingEnabled(false)
            assertEquals(listOf(false, true, false), seen)
        }

    @Test
    fun `a debug build never collects, whatever the preference`() =
        runTest {
            start(isDebugBuild = true)
            prefs.setCrashReportingEnabled(true)
            assertTrue("collection was switched on in a debug build: $seen", seen.isNotEmpty() && seen.none { it })
        }
}
