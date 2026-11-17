package com.cooper.binge.companion.jellyfin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.binge.companion.jellyfin.ADDRESS_POLICY_SWITCH
import com.cooper.binge.companion.jellyfin.JellyfinApp
import com.cooper.binge.companion.jellyfin.SEARCH_TOGGLES_SWITCH
import com.cooper.binge.companion.jellyfin.SERVER_REGISTRY_SWITCH
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only. Writes a switch: `.SERVER_REGISTRY` says whether the saved session lives in the shared library's per-server
 * stores, and `.ADDRESS_POLICY` whether a typed address is read, and plain HTTP guarded, the way the library does, and `.SEARCH_TOGGLES` whether a settings-search
 * result for an on/off setting carries its switch. Each is
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
        val action = intent.action.orEmpty()
        val name =
            when {
                action.endsWith(".ADDRESS_POLICY") -> ADDRESS_POLICY_SWITCH
                action.endsWith(".SEARCH_TOGGLES") -> SEARCH_TOGGLES_SWITCH
                else -> SERVER_REGISTRY_SWITCH
            }
        val key = booleanPreferencesKey(name)
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
