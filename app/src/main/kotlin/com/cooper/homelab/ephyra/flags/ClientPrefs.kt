package com.cooper.homelab.ephyra.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val CLIENT = booleanPreferencesKey("client")

/** Whether the user wants the client surfaces. On until turned off; it only counts while the remote flag is on (see [PreferenceGate]). */
class ClientPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { it[CLIENT] ?: true }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[CLIENT] = enabled }
    }
}
