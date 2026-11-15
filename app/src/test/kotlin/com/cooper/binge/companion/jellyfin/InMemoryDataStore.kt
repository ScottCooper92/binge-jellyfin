package com.cooper.binge.companion.jellyfin

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A synchronous preferences store: each write has reached every collector by the time it returns. Like the real DataStore it
 * runs one update at a time, so two writers each see the other's result: without that, a write made while another was in
 * flight could be lost, and a test waiting on it timed out now and then.
 */
class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val updates = Mutex()
    override val data: StateFlow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        updates.withLock { transform(state.value).also { state.value = it } }
}
