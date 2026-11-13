package com.cooper.binge.companion.jellyfin.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

private val FIRST_LAUNCH = longPreferencesKey("first_launch")
private val POSITIVE_ACTIONS = intPreferencesKey("positive_actions")
private val LAST_PROMPT = longPreferencesKey("last_prompt")

/** Reads and writes [ReviewSignals] on the device. Nothing here leaves it. */
class ReviewSignalStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun signals(): ReviewSignals =
        dataStore.data.first().let {
            ReviewSignals(it[FIRST_LAUNCH] ?: 0, it[POSITIVE_ACTIONS] ?: 0, it[LAST_PROMPT] ?: 0)
        }

    /** Stamps the first launch if unset; a second call never moves the clock. */
    suspend fun stampFirstLaunchIfUnset(nowEpochMillis: Long) {
        dataStore.edit { if (it[FIRST_LAUNCH] == null) it[FIRST_LAUNCH] = nowEpochMillis }
    }

    suspend fun incrementPositiveActionCount() {
        dataStore.edit { it[POSITIVE_ACTIONS] = (it[POSITIVE_ACTIONS] ?: 0) + 1 }
    }

    /** Records that the flow was requested, which is what the cooldown runs from. */
    suspend fun recordPromptRequested(nowEpochMillis: Long) {
        dataStore.edit { it[LAST_PROMPT] = nowEpochMillis }
    }
}
