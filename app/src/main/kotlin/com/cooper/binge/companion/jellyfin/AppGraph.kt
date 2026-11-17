package com.cooper.binge.companion.jellyfin

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.binge.companion.kit.KeystoreSecretCipher
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.auth.TOKEN_KEY_ALIAS
import com.cooper.binge.companion.jellyfin.demo.DemoTransport
import com.cooper.binge.companion.jellyfin.notifications.ActivityChecker
import com.cooper.binge.companion.jellyfin.notifications.AndroidBackgroundAccess
import com.cooper.binge.companion.jellyfin.notifications.AndroidNotifier
import com.cooper.binge.companion.jellyfin.notifications.BackgroundAccess
import com.cooper.binge.companion.jellyfin.notifications.NotificationPlanner
import com.cooper.binge.companion.jellyfin.notifications.NotificationPrefs
import com.cooper.binge.companion.jellyfin.notifications.NotificationScheduler
import com.cooper.binge.companion.jellyfin.notifications.PollReactor
import com.cooper.binge.companion.jellyfin.notifications.PollRunner
import com.cooper.binge.companion.jellyfin.notifications.WorkManagerNotificationScheduler
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.ServerEvents
import com.cooper.binge.companion.jellyfin.server.UdpDiscovery
import com.cooper.binge.companion.jellyfin.server.lanTargets
import com.cooper.binge.companion.jellyfin.ui.console.LicenceSource
import com.cooper.binge.companion.jellyfin.ui.console.UserPictures
import com.cooper.binge.companion.jellyfin.ui.console.clearSharedLogs
import com.cooper.binge.companion.jellyfin.ui.search.RecentSearches
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FirebaseRemoteFlagSource
import com.cooper.homelab.core.flags.Flag
import com.cooper.homelab.core.flags.RemoteFeatureFlags
import com.cooper.homelab.core.flags.StaticFlagOverrides
import com.cooper.homelab.core.foundation.AppDispatchers
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.review.PlayReviewPromptLauncher
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.review.ReviewSignalStore
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.AnalyticsConsentGate
import com.cooper.homelab.core.telemetry.AnalyticsDecorator
import com.cooper.homelab.core.telemetry.CrashReportingSwitch
import com.cooper.homelab.core.telemetry.FirebaseCrashCollection
import com.cooper.homelab.core.telemetry.GatedAnalytics
import com.cooper.homelab.core.telemetry.PostHogClient
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.telemetry.postHogConfig
import com.cooper.homelab.core.tip.DebugTipBilling
import com.cooper.homelab.core.tip.PlayTipBilling
import com.cooper.homelab.core.tip.TipBilling
import com.posthog.android.PostHogAndroid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            // Answers the demo server's address from memory and passes every other host through.
            .addInterceptor(DemoTransport())
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
        val fixed = if (BuildConfig.BINGE_PROMOTION) mapOf(Flag.BingePromotion to true) else emptyMap()
        RemoteFeatureFlags(
            source = FirebaseRemoteFlagSource(appContext),
            overrides = StaticFlagOverrides(fixed),
            scope = scope,
            io = io,
            initialOverrides = fixed,
        )
    }

    /** The user's answers about usage data and crash reports, in a store of their own. */
    val telemetryPrefs: TelemetryPrefs by lazy {
        TelemetryPrefs(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("telemetry") },
            ),
            // Crash reporting is opt-in here: it is off until the user agrees on the first-run screen or in Settings.
            crashReportingDefault = false,
        )
    }

    /** The administrator's background notifications: their own store, off until the switch is turned on. */
    val notificationPrefs: NotificationPrefs by lazy {
        NotificationPrefs(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("notifications") },
            ),
        )
    }

    /** The settings search's last few results, kept on this device alone. */
    val recentSearches: RecentSearches by lazy {
        RecentSearches(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("recent_searches") },
            ),
        )
    }

    val notifier: AndroidNotifier by lazy { AndroidNotifier(appContext) }

    val notificationScheduler: NotificationScheduler by lazy { WorkManagerNotificationScheduler(appContext) }

    val backgroundAccess: BackgroundAccess by lazy { AndroidBackgroundAccess(appContext) }

    /** What the periodic worker runs. */
    val pollRunner: PollRunner by lazy {
        PollRunner(
            ActivityChecker(connection, notificationPrefs, notifier),
            PollReactor(notifier, notificationScheduler, notificationPrefs),
        )
    }

    /** Keeps the poll scheduled while an administrator is signed in and has the switch on. */
    val notificationPlanner: NotificationPlanner by lazy {
        NotificationPlanner(sessions, notificationPrefs, notificationScheduler, notifier, scope)
    }

    /** Asks Play for its review card once the user has done enough and waited long enough; off until the flag is on. */
    val reviewPrompt: ReviewPrompt by lazy {
        ReviewPrompt(
            store =
                ReviewSignalStore(
                    PreferenceDataStoreFactory.create(
                        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                        produceFile = { appContext.preferencesDataStoreFile("review") },
                    ),
                ),
            launcher = PlayReviewPromptLauncher(appContext, AppDispatchers.platform()),
            isEnabled = { flags.flags.value.reviewPrompt },
            scope = scope,
            clock = Clock.system(),
        )
    }

    /** Asks for the review card in the app's scope, so the ask outlives the screen that made it. */
    fun askForReview(activity: Activity) {
        scope.launch { reviewPrompt.maybeAsk(activity) }
    }

    /** The tip jar's store: Google Play in a release build, a stand-in in a debug one, which is installed over adb where Play sells nothing. */
    internal val tipBilling: TipBilling by lazy { if (BuildConfig.DEBUG) DebugTipBilling() else PlayTipBilling(appContext) }

    /** The one owner of "may analytics report"; every backend registers with it. */
    val consentGate: AnalyticsConsentGate by lazy {
        AnalyticsConsentGate(telemetryPrefs, scope, LogcatLogger())
    }

    /** PostHog behind the consent gate. A build with no project key has no client, and then every call is a no-op. */
    val analytics: Analytics by lazy {
        val client =
            BuildConfig.POSTHOG_API_KEY
                .takeIf { it.isNotBlank() }
                ?.let { PostHogClient(PostHogAndroid.with(appContext, postHogConfig(it, BuildConfig.POSTHOG_HOST))) }
        GatedAnalytics(client, consentGate, AnalyticsDecorator.None, scope)
    }

    /** Holds Crashlytics' collection to the user's crash-reporting answer. */
    val crashReporting: CrashReportingSwitch by lazy {
        CrashReportingSwitch(FirebaseCrashCollection(appContext), telemetryPrefs, scope, isDebugBuild = BuildConfig.DEBUG)
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

    /** Everything the app keeps on disk about the account that was signed in: its pictures and the last log shared. */
    suspend fun clearAccountCaches() =
        withContext(io) {
            userPictures.clear()
            clearSharedLogs(appContext.cacheDir)
        }

    val userPictures: UserPictures by lazy { UserPictures(connection, File(appContext.cacheDir, "user-pictures")) }

    /** Lives as long as the process: the event socket is shared by every Observe stream in it. */
    private val scope = CoroutineScope(SupervisorJob())

    val events: ServerEvents by lazy { ServerEvents(connection, index, scope, io) }

    /** Jellyfin's UDP broadcast, on this device's LAN networks. */
    val discovery: UdpDiscovery by lazy { UdpDiscovery(targets = { lanTargets(appContext) }, io = io) }
}

/** The client name Jellyfin shows for this app, in its dashboard's devices, sessions and activity. */
internal const val CLIENT_NAME = "Ephyra"
