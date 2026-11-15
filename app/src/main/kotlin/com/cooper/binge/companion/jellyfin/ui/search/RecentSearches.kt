package com.cooper.binge.companion.jellyfin.ui.search

import android.content.res.Resources
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val RECENT = stringPreferencesKey("recent_entries")

/** How many entries the empty search page remembers, as Android's Settings does. */
internal const val MAX_RECENT = 5

private const val SEPARATOR = "\n"

/**
 * A stable name for an entry, to remember it by: its page and its label's resource name. A resource's number changes from build to
 * build, its name does not. The entry, never what the user typed, is what is kept.
 */
internal fun SettingEntry.id(resources: Resources): String = "${page.name}:${resources.getResourceEntryName(label)}"

/**
 * The entries the user last opened from the settings search, newest first, in a DataStore of their own on this device. Nothing here is
 * sent anywhere. An id whose entry the app no longer has, or the user may no longer open, is simply not shown.
 */
class RecentSearches(
    private val dataStore: DataStore<Preferences>,
) {
    internal val ids: Flow<List<String>> =
        dataStore.data.map { it[RECENT].orEmpty().split(SEPARATOR).filter(String::isNotBlank) }.distinctUntilChanged()

    /** [id] becomes the newest; one already there moves up rather than twice, and only the last [MAX_RECENT] stay. */
    internal suspend fun remember(id: String) {
        dataStore.edit { prefs ->
            val kept = prefs[RECENT].orEmpty().split(SEPARATOR).filter { it.isNotBlank() && it != id }
            prefs[RECENT] = (listOf(id) + kept).take(MAX_RECENT).joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(RECENT) }
    }
}
