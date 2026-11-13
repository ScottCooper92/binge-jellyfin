package com.cooper.binge.companion.jellyfin.telemetry

import android.content.Context
import com.cooper.binge.companion.jellyfin.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Where crash reports go, as far as the switch is concerned: on or off. */
fun interface CrashCollection {
    fun setEnabled(enabled: Boolean)
}

/**
 * Crashlytics' collection switch. A build without this app's `google-services.json` has no Firebase
 * app to report into, so there it does nothing rather than fail. Turning collection off also deletes
 * any report not yet sent.
 */
class FirebaseCrashCollection(
    private val context: Context,
) : CrashCollection {
    override fun setEnabled(enabled: Boolean) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.isCrashlyticsCollectionEnabled = enabled
        // Reports are written to disk even while collection is off, and sent once it is on. Clear
        // them, so a crash from before the user agreed is never uploaded after.
        if (!enabled) crashlytics.deleteUnsentReports()
    }
}

/**
 * Holds crash collection to the user's crash-reporting preference, for the life of the app. A debug
 * build never collects, whatever the preference says. No user id is ever set: a crash is reported
 * against the install, not a person.
 */
class CrashReportingSwitch(
    private val collection: CrashCollection,
    private val prefs: TelemetryPrefs,
    private val scope: CoroutineScope,
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
) {
    fun start() {
        scope.launch {
            prefs.crashReportingEnabled.distinctUntilChanged().collect { enabled ->
                collection.setEnabled(!isDebugBuild && enabled)
            }
        }
    }
}
