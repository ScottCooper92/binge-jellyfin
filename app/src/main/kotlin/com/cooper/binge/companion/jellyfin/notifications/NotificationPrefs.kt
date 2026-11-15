package com.cooper.binge.companion.jellyfin.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val ENABLED = booleanPreferencesKey("enabled")
private val INTERVAL = intPreferencesKey("poll_interval_minutes")
private val PAUSED = booleanPreferencesKey("paused_for_auth_failure")
private val CURSOR_ID = longPreferencesKey("cursor_id")
private val CURSOR_DATE = longPreferencesKey("cursor_date_millis")
private val CURSOR_OWNER = stringPreferencesKey("cursor_owner")

/** How often the poll may run, in minutes: WorkManager's 15 is the floor, so these are the steps from there. */
val POLL_INTERVAL_CHOICES: List<Int> = listOf(15, 30, 60, 120, 360)

const val DEFAULT_POLL_INTERVAL_MINUTES: Int = 60

/** How far into the activity log the poll has read: the newest entry's id and date, and whose log it was. */
data class ActivityCursor(
    val id: Long,
    val dateMillis: Long?,
)

/**
 * The poll's own DataStore. Notifications are off until an administrator turns them on. The cursor is null until the first check
 * seeds it, which notifies nothing, so turning the switch on does not announce the backlog.
 */
class NotificationPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }.distinctUntilChanged()

    /** Turning it on drops the cursor, so what the log gathered while it was off is not announced. */
    suspend fun setEnabled(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[ENABLED] = value
            if (value) prefs.clearCursor()
        }
    }

    /** How often the poll is asked to run, always one of [POLL_INTERVAL_CHOICES]; Android may run it less often. */
    val intervalMinutes: Flow<Int> =
        dataStore.data
            .map { prefs -> prefs[INTERVAL]?.takeIf { it in POLL_INTERVAL_CHOICES } ?: DEFAULT_POLL_INTERVAL_MINUTES }
            .distinctUntilChanged()

    suspend fun setIntervalMinutes(minutes: Int) {
        require(minutes in POLL_INTERVAL_CHOICES) { "No poll interval of $minutes minutes" }
        dataStore.edit { it[INTERVAL] = minutes }
    }

    /** Set when the server refuses the saved sign-in, so the poll stops until the user signs in again. */
    val pausedForAuthFailure: Flow<Boolean> = dataStore.data.map { it[PAUSED] ?: false }.distinctUntilChanged()

    suspend fun setPausedForAuthFailure(paused: Boolean) {
        dataStore.edit { it[PAUSED] = paused }
    }

    /** The log read so far, or null when it has not been read for [owner] (a server and user) yet. */
    suspend fun cursor(owner: String): ActivityCursor? {
        val prefs = dataStore.data.first()
        if (prefs[CURSOR_OWNER] != owner) return null
        val id = prefs[CURSOR_ID] ?: return null
        return ActivityCursor(id, prefs[CURSOR_DATE])
    }

    suspend fun setCursor(
        owner: String,
        cursor: ActivityCursor,
    ) {
        dataStore.edit { prefs ->
            prefs[CURSOR_OWNER] = owner
            prefs[CURSOR_ID] = cursor.id
            cursor.dateMillis?.let { prefs[CURSOR_DATE] = it } ?: prefs.remove(CURSOR_DATE)
        }
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.clearCursor() {
    remove(CURSOR_OWNER)
    remove(CURSOR_ID)
    remove(CURSOR_DATE)
}
