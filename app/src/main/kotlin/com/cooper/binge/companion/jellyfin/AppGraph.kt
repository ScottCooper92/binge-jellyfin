package com.cooper.binge.companion.jellyfin

import android.content.Context
import android.os.Build
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.binge.companion.kit.KeystoreSecretCipher
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.auth.TOKEN_KEY_ALIAS
import com.cooper.binge.companion.jellyfin.flags.FeatureFlags
import com.cooper.binge.companion.jellyfin.flags.FirebaseRemoteFlagSource
import com.cooper.binge.companion.jellyfin.flags.Flag
import com.cooper.binge.companion.jellyfin.flags.RemoteFeatureFlags
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.ServerEvents
import com.cooper.binge.companion.jellyfin.server.UdpDiscovery
import com.cooper.binge.companion.jellyfin.server.lanTargets
import com.cooper.binge.companion.jellyfin.telemetry.Analytics
import com.cooper.binge.companion.jellyfin.telemetry.AnalyticsConsentGate
import com.cooper.binge.companion.jellyfin.telemetry.CrashReportingSwitch
import com.cooper.binge.companion.jellyfin.telemetry.FirebaseCrashCollection
import com.cooper.binge.companion.jellyfin.telemetry.PostHogAnalytics
import com.cooper.binge.companion.jellyfin.telemetry.PostHogClient
import com.cooper.binge.companion.jellyfin.telemetry.TelemetryPrefs
import com.cooper.binge.companion.jellyfin.telemetry.postHogConfig
import com.cooper.binge.companion.jellyfin.ui.console.LicenceSource
import com.cooper.binge.companion.jellyfin.ui.console.UserPictures
import com.posthog.android.PostHogAndroid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** The app's object graph, built by hand. One of each for the life of the process. */
class AppGraph(
    context: Context,
    /** Where every server call runs. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    /** The licences of the libraries this app ships, read once for the licences page. */
    val licences: LicenceSource by lazy { LicenceSource(appContext, io) }

    val http: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val sessions: SessionStore by lazy {
        SessionStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("session") },
            ),
            KeystoreSecretCipher(TOKEN_KEY_ALIAS),
            io,
        )
    }

    /**
     * Whether the app names Binge: Remote Config in this app's Firebase project, off by default. A build
     * made with -PbingePromotion=true turns it on locally, whatever the remote says.
     */
    val flags: FeatureFlags by lazy {
        RemoteFeatureFlags(
            source = FirebaseRemoteFlagSource(appContext),
            overrides = if (BuildConfig.BINGE_PROMOTION) mapOf(Flag.BingePromotion to true) else emptyMap(),
            io = io,
        )
    }

    /** The user's answers about usage data and crash reports, in a store of their own. */
    val telemetryPrefs: TelemetryPrefs by lazy {
        TelemetryPrefs(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("telemetry") },
            ),
        )
    }

    /** The one owner of "may analytics report"; every backend registers with it. */
    val consentGate: AnalyticsConsentGate by lazy { AnalyticsConsentGate(telemetryPrefs, scope) }

    /** PostHog behind the consent gate. A build with no project key has no client, and then every call is a no-op. */
    val analytics: Analytics by lazy {
        val client =
            BuildConfig.POSTHOG_API_KEY
                .takeIf { it.isNotBlank() }
                ?.let { PostHogClient(PostHogAndroid.with(appContext, postHogConfig(it, BuildConfig.POSTHOG_HOST))) }
        PostHogAnalytics(client, consentGate, scope)
    }

    /** Holds Crashlytics' collection to the user's crash-reporting answer. */
    val crashReporting: CrashReportingSwitch by lazy {
        CrashReportingSwitch(FirebaseCrashCollection(appContext), telemetryPrefs, scope)
    }

    val connection: Connection by lazy {
        Connection(
            http = http,
            sessions = sessions,
            client = CLIENT_NAME,
            device = Build.MODEL ?: "Android",
            version = BuildConfig.VERSION_NAME,
            io = io,
        )
    }

    val index: LibraryIndex by lazy { LibraryIndex() }

    val userPictures: UserPictures by lazy { UserPictures(connection, File(appContext.cacheDir, "user-pictures")) }

    /** Lives as long as the process: the event socket is shared by every Observe stream in it. */
    private val scope = CoroutineScope(SupervisorJob())

    val events: ServerEvents by lazy { ServerEvents(connection, index, scope, io) }

    /** Jellyfin's UDP broadcast, on this device's LAN networks. */
    val discovery: UdpDiscovery by lazy { UdpDiscovery(targets = { lanTargets(appContext) }, io = io) }
}

/** The client name Jellyfin shows for this app, in its dashboard's devices, sessions and activity. */
internal const val CLIENT_NAME = "Ephyra"
