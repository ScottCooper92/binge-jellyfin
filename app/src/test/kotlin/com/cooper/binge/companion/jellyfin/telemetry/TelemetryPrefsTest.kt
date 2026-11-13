package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the stored answers start as, and how the first-run question and Settings write them. */
class TelemetryPrefsTest {
    private val prefs = TelemetryPrefs(InMemoryDataStore())

    @Test
    fun `nothing is on until the user says so`() =
        runTest {
            assertEquals(AnalyticsConsent.UNDECIDED, prefs.analyticsConsent.first())
            assertFalse(prefs.crashReportingEnabled.first())
        }

    @Test
    fun `agreeing on the first-run screen turns on both usage data and crash reports`() =
        runTest {
            prefs.answerFirstRun(granted = true)
            assertEquals(AnalyticsConsent.GRANTED, prefs.analyticsConsent.first())
            assertTrue(prefs.crashReportingEnabled.first())
        }

    @Test
    fun `declining on the first-run screen is an answer, and leaves both off`() =
        runTest {
            prefs.answerFirstRun(granted = false)
            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())
            assertFalse(prefs.crashReportingEnabled.first())
        }

    @Test
    fun `Settings changes each on its own after the first answer`() =
        runTest {
            prefs.answerFirstRun(granted = true)
            prefs.setCrashReportingEnabled(false)
            assertEquals(AnalyticsConsent.GRANTED, prefs.analyticsConsent.first())
            assertFalse(prefs.crashReportingEnabled.first())

            prefs.setAnalyticsGranted(false)
            prefs.setCrashReportingEnabled(true)
            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())
            assertTrue(prefs.crashReportingEnabled.first())
        }
}
