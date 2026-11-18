package com.cooper.homelab.ephyra

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * What an install already answered stays as it was, and is never asked again; only a store with no answer asks. The keys are the
 * ones every earlier version wrote, so a test writes them by name rather than through the library.
 */
class TelemetryAnswersTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = {
            folder.newFile("telemetry.preferences_pb")
        })

    private fun prefs(store: DataStore<Preferences>) = TelemetryPrefs(store, crashReportingDefault = false)

    @Test
    fun `a store with no answer asks and keeps crash reports off`() =
        runTest {
            val prefs = prefs(store())
            assertEquals(AnalyticsConsent.UNDECIDED, prefs.analyticsConsent.first())
            assertEquals(false, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `an install that declined usage data but kept crash reports keeps both as they were`() =
        runTest {
            val store = store()
            store.edit {
                it[booleanPreferencesKey("analytics_granted")] = false
                it[booleanPreferencesKey("crash_reporting_enabled")] = true
            }
            val prefs = prefs(store)
            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())
            assertEquals(true, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `an install that agreed to usage data only is answered and its crash reports stay off`() =
        runTest {
            val store = store()
            store.edit { it[booleanPreferencesKey("analytics_granted")] = true }
            val prefs = prefs(store)
            assertEquals(AnalyticsConsent.GRANTED, prefs.analyticsConsent.first())
            assertEquals(false, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `not now on the first run turns both off in one answer`() =
        runTest {
            val prefs = prefs(store())
            prefs.answerFirstRun(false)
            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())
            assertEquals(false, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `share on the first run turns both on`() =
        runTest {
            val prefs = prefs(store())
            prefs.answerFirstRun(true)
            assertEquals(AnalyticsConsent.GRANTED, prefs.analyticsConsent.first())
            assertEquals(true, prefs.crashReportingEnabled.first())
        }
}
