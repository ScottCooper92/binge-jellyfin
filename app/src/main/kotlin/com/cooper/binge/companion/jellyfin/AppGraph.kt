package com.cooper.binge.companion.jellyfin

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.binge.companion.kit.KeystoreSecretCipher
import com.cooper.binge.companion.jellyfin.auth.JellyfinLoginCodec
import com.cooper.binge.companion.jellyfin.auth.LegacySessionImport
import com.cooper.binge.companion.jellyfin.auth.LegacySessionStore
import com.cooper.binge.companion.jellyfin.auth.RegistrySessionStore
import com.cooper.binge.companion.jellyfin.auth.ServerIdentity
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.auth.TOKEN_KEY_ALIAS
import com.cooper.binge.companion.jellyfin.auth.forLibrary
import com.cooper.binge.companion.jellyfin.demo.DemoTransport
import com.cooper.binge.companion.jellyfin.notifications.ActivityChecker
import com.cooper.binge.companion.jellyfin.notifications.ActivitySwitch
import com.cooper.binge.companion.jellyfin.notifications.AndroidBackgroundAccess
import com.cooper.binge.companion.jellyfin.notifications.AndroidNotifier
import com.cooper.binge.companion.jellyfin.notifications.BackgroundAccess
import com.cooper.binge.companion.jellyfin.notifications.LegacyActivityCursor
import com.cooper.binge.companion.jellyfin.notifications.NotificationPlanner
import com.cooper.binge.companion.jellyfin.notifications.NotificationPrefs
import com.cooper.binge.companion.jellyfin.notifications.PollRunner
import com.cooper.binge.companion.jellyfin.notifications.pollReactor
import com.cooper.binge.companion.jellyfin.notifications.workManagerPollScheduler
import com.cooper.binge.companion.jellyfin.server.AddressPolicy
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.ServerEvents
import com.cooper.binge.companion.jellyfin.server.UdpDiscovery
import com.cooper.binge.companion.jellyfin.server.lanTargets
import com.cooper.binge.companion.jellyfin.ui.console.LicenceSource
import com.cooper.binge.companion.jellyfin.ui.console.UserPictures
import com.cooper.binge.companion.jellyfin.ui.console.clearSharedLogs
import com.cooper.binge.companion.jellyfin.ui.search.RecentSearches
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FirebaseRemoteFlagSource
import com.cooper.homelab.core.flags.Flag
import com.cooper.homelab.core.flags.RemoteFeatureFlags
import com.cooper.homelab.core.flags.StaticFlagOverrides
import com.cooper.homelab.core.foundation.AppDispatchers
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.notifications.DataStoreFeedCursorStore
import com.cooper.homelab.core.notifications.FeedCursorStore
import com.cooper.homelab.core.notifications.PollScheduler
import com.cooper.homelab.core.review.PlayReviewPromptLauncher
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.review.ReviewSignalStore
import com.cooper.homelab.core.server.connection.DeviceIdentity
import com.cooper.homelab.core.server.connection.ServerRegistry
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.CleartextGate
import com.cooper.homelab.core.server.network.CleartextGuard
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.server.network.isLocalOrPrivateHost
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.AnalyticsConsentGate
import com.cooper.homelab.core.telemetry.AnalyticsDecorator
import com.cooper.homelab.core.telemetry.CrashReportingSwitch
import com.cooper.homelab.core.telemetry.FirebaseCrashCollection
import com.cooper.homelab.core.telemetry.GatedAnalytics
import com.cooper.homelab.core.telemetry.NoOpCrashBreadcrumbs
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/** The app's object graph, built by hand. One of each for the life of the process. */
class AppGraph(
    context: Context,
    /** Where every server call runs. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    /** The licences of the libraries this app ships, read once for the licences page. */
    val licences: LicenceSource by lazy { LicenceSource(appContext, io) }

    /** Whether the app reads a typed address and guards plain HTTP the way the shared library does. See [serverRegistryEnabled]. */
    private val addressPolicyEnabled: Boolean by lazy { debugSwitch(ADDRESS_POLICY_SWITCH) }

    /** The addresses to try for a typed one: the released behaviour, unless the debug switch is on. */
    val addressPolicy: AddressPolicy by lazy { if (addressPolicyEnabled) AddressPolicy.Shared else AddressPolicy.Legacy }

    val http: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            // Answers the demo server's address from memory and passes every other host through.
            .addInterceptor(DemoTransport())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                // A network interceptor, so it judges every hop, a redirect down to HTTP included. The demo never reaches it.
                if (addressPolicyEnabled) {
                    addNetworkInterceptor(
                        CleartextGuard(CleartextGate(cleartextConsent, publicCleartextAvailable = true)),
                    )
                }
            }.build()
    }

    /**
     * The public hosts the user has agreed to reach over plain HTTP. A user already connected over plain HTTP to a public host
     * is grandfathered in, as the library asks, so the guard does not cut off a connection they set up knowingly.
     */
    private val cleartextConsent: CleartextConsent by lazy {
        DataStoreCleartextConsent(dataStore("cleartext_consent")) {
            sessions
                .current()
                ?.baseUrl
                ?.toHttpUrlOrNull()
                ?.takeIf { !it.isHttps && !isLocalOrPrivateHost(it.host) }
                ?.host
        }
    }

    private fun dataStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            produceFile = { appContext.preferencesDataStoreFile(name) },
        )

    /** Debug builds only: the answers a developer gives to switches a shipped build never reads. */
    internal val debugSwitches: DataStore<Preferences> by lazy { dataStore(DEBUG_SWITCHES_FILE) }

    /**
     * Whether the saved session lives in the shared library's per-server stores. Off unless a debug build's switch file says so, and
     * read once, so the choice is fixed for the life of the process and a flip takes effect on the next launch. It is not
     * remote-controlled. The read blocks the main thread once, which is why it stays behind `BuildConfig.DEBUG`.
     */
    private val serverRegistryEnabled: Boolean by lazy { debugSwitch(SERVER_REGISTRY_SWITCH) }

    /** A debug switch: off in a shipped build, which never opens the file, and otherwise what the switch file holds. */
    private fun debugSwitch(name: String): Boolean =
        BuildConfig.DEBUG && runBlocking { debugSwitches.data.first()[booleanPreferencesKey(name)] } == true

    /** This install's one server id, the same whichever store holds the session. Its own file, which a sign-out does not clear. */
    val serverIdentity: ServerIdentity by lazy { ServerIdentity(dataStore("server_identity")) }

    val sessions: SessionStore by lazy {
        val cipher = KeystoreSecretCipher(TOKEN_KEY_ALIAS)
        val legacyFile = dataStore("session")
        val legacy = LegacySessionStore(legacyFile, cipher, io)
        if (!serverRegistryEnabled) return@lazy legacy
        // Logcat, not the ring: the ring redacts through `sessions`, so asking it for a logger here would be a cycle.
        val logger = LogcatLogger()
        val registryFile = dataStore("servers")
        val registry = ServerRegistry(registryFile, "registry", { UUID.randomUUID().toString() }, logger)
        val logins = LoginStore(dataStore("server_logins"), JellyfinLoginCodec, cipher.forLibrary(), logger)
        val device = DeviceIdentity(registryFile, "device_id") { UUID.randomUUID().toString() }
        val importer = LegacySessionImport(legacyFile, registry, logins, device, registryFile, serverIdentity, logger)
        RegistrySessionStore(registry, logins, importer, serverIdentity, device, io)
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

    private val notificationStore: DataStore<Preferences> by lazy { dataStore("notifications") }

    /** The administrator's background notifications: their own store, off until the switch is turned on. */
    val notificationPrefs: NotificationPrefs by lazy { NotificationPrefs(notificationStore) }

    /** Where the poll has read up to, per server and user. It shares the notifications file, under keys of its own. */
    private val feedCursors: FeedCursorStore by lazy { DataStoreFeedCursorStore(notificationStore, "feed_cursor") }

    /** The notifications switch: turning it on makes the next poll seed again. */
    val activitySwitch: ActivitySwitch by lazy { ActivitySwitch(notificationPrefs, feedCursors, serverIdentity) }

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

    val notificationScheduler: PollScheduler by lazy { workManagerPollScheduler(appContext) }

    val backgroundAccess: BackgroundAccess by lazy { AndroidBackgroundAccess(appContext) }

    /** What the periodic worker runs. */
    val pollRunner: PollRunner by lazy {
        PollRunner(
            ActivityChecker(connection, feedCursors, LegacyActivityCursor(notificationPrefs), serverIdentity, notifier, logger),
            pollReactor(notifier, notificationScheduler, notificationPrefs) {
                sessions.session.first() != null && notificationPrefs.enabled.first() && !notificationPrefs.pausedForAuthFailure.first()
            },
            serverIdentity,
        )
    }

    /** Keeps the poll scheduled while an administrator is signed in and has the switch on. */
    val notificationPlanner: NotificationPlanner by lazy {
        NotificationPlanner(sessions, notificationPrefs, serverIdentity, notificationScheduler, notifier, scope, NoOpCrashBreadcrumbs)
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
        AnalyticsConsentGate(telemetryPrefs, scope, logger)
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
            logger = logger,
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

    /** Told what the session holds, so an entry that names it is redacted. A debug build says so when one had to be. */
    private val redactor: Redactor by lazy {
        Redactor(onRedaction = { if (BuildConfig.DEBUG) Log.w(REDACTOR_TAG, "A log entry held a registered literal") })
            .also { it.followSession(sessions.session, scope) }
    }

    /** The app's log: held in memory and never written to disk. A debug build also forwards each redacted entry to Logcat. */
    private val ring: RingLog by lazy {
        RingLog(
            clock = Clock.system(),
            redactor = redactor,
            // One saved server: no entry carries a server field today, so there is no order to label.
            serverOrder = { emptyList() },
            forward = LogcatLogger().takeIf { BuildConfig.DEBUG },
            // The frames kept from a logged throwable are this app's own, named here so the library holds no default.
            ownPackages = listOf("com.cooper.binge."),
        )
    }

    val logger: Logger get() = ring

    val events: ServerEvents by lazy { ServerEvents(connection, index, scope, io) }

    /** Jellyfin's UDP broadcast, on this device's LAN networks. */
    val discovery: UdpDiscovery by lazy { UdpDiscovery(targets = { lanTargets(appContext, logger) }, io = io, logger = logger) }
}

/** The client name Jellyfin shows for this app, in its dashboard's devices, sessions and activity. */
internal const val CLIENT_NAME = "Ephyra"

/** The debug switches' file and the key in it that turns the per-server stores on. A shipped build never reads either. */
internal const val DEBUG_SWITCHES_FILE = "debug_switches"
internal const val SERVER_REGISTRY_SWITCH = "server_registry"
internal const val ADDRESS_POLICY_SWITCH = "address_policy"
private const val REDACTOR_TAG = "Redactor"
