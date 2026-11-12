package com.cooper.binge.companion.jellyfin.telemetry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The user's analytics decision. [UNDECIDED] is what an unanswered prompt reads as, and it is kept
 * apart from [DENIED] so setup can tell "not asked yet" from "said no". Only [GRANTED] opens the gate.
 */
enum class AnalyticsConsent { UNDECIDED, GRANTED, DENIED }

private val ANALYTICS_GRANTED = booleanPreferencesKey("analytics_granted")

/**
 * The reporting preferences, in a DataStore of their own. Analytics is opt-in: an absent key is
 * [AnalyticsConsent.UNDECIDED].
 */
class TelemetryPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val analyticsConsent: Flow<AnalyticsConsent> =
        dataStore.data
            .map { prefs ->
                when (prefs[ANALYTICS_GRANTED]) {
                    true -> AnalyticsConsent.GRANTED
                    false -> AnalyticsConsent.DENIED
                    null -> AnalyticsConsent.UNDECIDED
                }
            }.distinctUntilChanged()

    /** Records an answer. There is no way back to [AnalyticsConsent.UNDECIDED]: once asked is asked. */
    suspend fun setAnalyticsGranted(granted: Boolean) {
        dataStore.edit { it[ANALYTICS_GRANTED] = granted }
    }
}
