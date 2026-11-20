package com.cooper.homelab.ephyra.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.homelab.core.flags.BooleanPreference

/**
 * Whether the user wants the client surfaces: the key a shipped build already wrote, and on until turned off. It only counts while the
 * remote flag is on (see `PreferenceGate`).
 */
internal fun clientPreference(dataStore: DataStore<Preferences>) = BooleanPreference(dataStore, key = "client", default = true)
