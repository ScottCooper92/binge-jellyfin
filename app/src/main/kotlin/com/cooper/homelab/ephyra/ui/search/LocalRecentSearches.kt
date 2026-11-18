package com.cooper.homelab.ephyra.ui.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.homelab.core.presentation.search.rememberRecent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.cooper.homelab.core.presentation.search.RecentSearches as SharedRecentSearches

private val RECENT = stringPreferencesKey("recent_entries")

private const val SEPARATOR = "\n"

/**
 * The entries the user last opened from the settings search, newest first, in a DataStore of their own on this device. Nothing here is
 * sent anywhere. An id whose entry the app no longer has, or the user may no longer open, is simply not shown.
 *
 * This is the shared library's `RecentSearches` over the file and key this app has always written (`recent_entries`), because it has one
 * account and the shared store keeps a list per server, under a key this app's users do not have yet. An app that keeps a second server
 * moves to the shared per-server store, `DataStoreRecentSearches`, and drops the few entries it had.
 */
class LocalRecentSearches(
    private val dataStore: DataStore<Preferences>,
) : SharedRecentSearches {
    override val ids: Flow<List<String>> =
        dataStore.data.map { it.read() }.distinctUntilChanged()

    /**
     * Opening a result calls this and leaves the search at once, which ends the caller's scope, so the write does not take its caller's
     * cancellation.
     */
    override suspend fun remember(id: String) {
        withContext(NonCancellable) {
            dataStore.edit { prefs -> prefs[RECENT] = rememberRecent(prefs.read(), id).joinToString(SEPARATOR) }
        }
    }

    override suspend fun clear() {
        withContext(NonCancellable) { dataStore.edit { it.remove(RECENT) } }
    }

    private fun Preferences.read(): List<String> = this[RECENT].orEmpty().split(SEPARATOR).filter { it.isNotBlank() }
}
