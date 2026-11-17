package com.cooper.binge.companion.jellyfin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.binge.companion.jellyfin.JellyfinApp
import com.cooper.binge.companion.jellyfin.SERVER_REGISTRY_SWITCH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Writes the switch that says whether the saved session lives in the shared library's per-server stores. It is
 * read once when the process starts, so it takes effect on the next launch. An `enabled` extra of false turns it back off, and no
 * extra clears it. The old file is never touched, so a flip back finds the session as the old file left it, and loses what was
 * changed in the new stores.
 */
class ServerRegistrySwitchReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        val enabled = if (intent.hasExtra(EXTRA_ENABLED)) intent.getBooleanExtra(EXTRA_ENABLED, false) else null
        val key = booleanPreferencesKey(SERVER_REGISTRY_SWITCH)
        val switches = (context.applicationContext as JellyfinApp).graph.debugSwitches
        CoroutineScope(Dispatchers.IO).launch {
            try {
                switches.edit { if (enabled == null) it.remove(key) else it[key] = enabled }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val EXTRA_ENABLED = "enabled"
    }
}
