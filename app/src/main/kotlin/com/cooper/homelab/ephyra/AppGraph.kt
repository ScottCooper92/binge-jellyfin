package com.cooper.homelab.ephyra

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
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.binge.companion.kit.KeystoreSecretCipher
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.diagnostics.follow
import com.cooper.homelab.core.flags.BooleanPreference
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FirebaseRemoteFlagSource
import com.cooper.homelab.core.flags.FlagPreferenceGate
import com.cooper.homelab.core.flags.PreferenceGate
import com.cooper.homelab.core.flags.RemoteFeatureFlags
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.core.flags.StaticFlagOverrides
import com.cooper.homelab.core.foundation.AppDispatchers
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.notifications.DataStoreFeedCursorStore
import com.cooper.homelab.core.notifications.FeedCursorStore
import com.cooper.homelab.core.notifications.PollScheduler
import com.cooper.homelab.core.presentation.appsettings.LicenceSource
import com.cooper.homelab.core.presentation.connect.LinkStash
import com.cooper.homelab.core.presentation.search.DataStoreQueryHistory
import com.cooper.homelab.core.presentation.search.QueryHistory
import com.cooper.homelab.core.review.PlayReviewPromptLauncher
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.review.ReviewSignalStore
import com.cooper.homelab.core.server.connection.DeviceIdentity
import com.cooper.homelab.core.server.connection.SecretCipher
import com.cooper.homelab.core.server.connection.ServerRegistry
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.CleartextGate
import com.cooper.homelab.core.server.network.CleartextGuard
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.AnalyticsConsentGate
import com.cooper.homelab.core.telemetry.AnalyticsDecorator
import com.cooper.homelab.core.telemetry.CrashReportingSwitch
import com.cooper.homelab.core.telemetry.FirebaseCrashBreadcrumbs
import com.cooper.homelab.core.telemetry.FirebaseCrashCollection
import com.cooper.homelab.core.telemetry.GatedAnalytics
import com.cooper.homelab.core.telemetry.PostHogClient
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.telemetry.postHogConfig
import com.cooper.homelab.core.tip.DebugTipBilling
import com.cooper.homelab.core.tip.PlayTipBilling
import com.cooper.homelab.core.tip.TipBilling
import com.cooper.homelab.ephyra.auth.JellyfinLoginCodec
import com.cooper.homelab.ephyra.auth.LegacySessionImport
import com.cooper.homelab.ephyra.auth.LegacySessionStore
import com.cooper.homelab.ephyra.auth.RegistrySessionStore
import com.cooper.homelab.ephyra.auth.ServerIdentity
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.auth.TOKEN_KEY_ALIAS
import com.cooper.homelab.ephyra.auth.forLibrary
import com.cooper.homelab.ephyra.demo.DemoTransport
import com.cooper.homelab.ephyra.flags.ClientEnabled
import com.cooper.homelab.ephyra.flags.RemoteFlagKeys
import com.cooper.homelab.ephyra.flags.clientPreference
import com.cooper.homelab.ephyra.notifications.ActivityChecker
import com.cooper.homelab.ephyra.notifications.ActivitySwitch
import com.cooper.homelab.ephyra.notifications.AndroidBackgroundAccess
import com.cooper.homelab.ephyra.notifications.AndroidNotifier
import com.cooper.homelab.ephyra.notifications.BackgroundAccess
import com.cooper.homelab.ephyra.notifications.LegacyActivityCursor
import com.cooper.homelab.ephyra.notifications.NotificationPlanner
import com.cooper.homelab.ephyra.notifications.NotificationPrefs
import com.cooper.homelab.ephyra.notifications.PollBreadcrumbs
import com.cooper.homelab.ephyra.notifications.PollRunner
import com.cooper.homelab.ephyra.notifications.pollReactor
import com.cooper.homelab.ephyra.notifications.workManagerPollScheduler
import com.cooper.homelab.ephyra.playback.PlaybackSittings
import com.cooper.homelab.ephyra.playback.ResourceAudioWording
import com.cooper.homelab.ephyra.playback.SignedInNextEpisodes
import com.cooper.homelab.ephyra.playback.SignedInPlayReporter
import com.cooper.homelab.ephyra.playback.SignedInSourceMinter
import com.cooper.homelab.ephyra.playback.StreamingCap
import com.cooper.homelab.ephyra.playback.StreamingQualityPrefs
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.ConnectivityStreamingNetwork
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.ServerEvents
import com.cooper.homelab.ephyra.server.UdpDiscovery
import com.cooper.homelab.ephyra.server.lanTargets
import com.cooper.homelab.ephyra.ui.SharedPreferencesLinkStash
import com.cooper.homelab.ephyra.ui.console.AboutLibrariesLicenceSource
import com.cooper.homelab.ephyra.ui.console.UserPictures
import com.cooper.homelab.ephyra.ui.console.clearSharedLogs
import com.cooper.homelab.ephyra.ui.search.LocalRecentSearches
import com.cooper.homelab.player.AudioFallbackMemory
import com.cooper.homelab.player.InAppPlayer
import com.cooper.homelab.player.PlayerViewModelFactory
import com.cooper.homelab.player.api.PlayerClock
import com.cooper.homelab.player.api.PlayerDispatchers
import com.posthog.android.PostHogAndroid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    /** The session store, for a test that signs in without the platform's keystore, which a unit test has none of. The app passes none. */
    private val sessionsForTest: SessionStore? = null,
) {
    private val appContext = context.applicationContext

    /** The licences of the libraries this app ships, read once for the licences page. */
    val licences: LicenceSource by lazy { AboutLibrariesLicenceSource(appContext, io) }

    /**
     * Whether a settings-search result for an on/off setting carries its switch. Off unless a debug build's switch file says so, and
     * read once, so a flip takes effect on the next launch. It is not remote-controlled.
     */
    val searchTogglesEnabled: Boolean by lazy { debugSwitch(SEARCH_TOGGLES_SWITCH) }

    val http: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            // Answers the demo server's address from memory and passes every other host through.
            .addInterceptor(DemoTransport())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // A network interceptor, so it judges every hop, a redirect down to HTTP included. The demo never reaches it.
            .addNetworkInterceptor(CleartextGuard(CleartextGate(cleartextConsent, publicCleartextAvailable = true)))
            .build()
    }

    /**
     * The public hosts the user has agreed to reach over plain HTTP. A user already connected over plain HTTP to a public host
     * is grandfathered in, as the library asks, so the guard does not cut off a connection they set up knowingly.
     */
    internal val cleartextConsent: CleartextConsent by lazy {
        DataStoreCleartextConsent(dataStore("cleartext_consent")) {
            insecurePublicHost(sessions.current()?.baseUrl)
        }
    }

    /** Drops the consent to a public plain-HTTP host once the user leaves that server. */
    fun startCleartextConsent() {
        cleartextConsent.followSession(sessions.session.map { it?.baseUrl }, scope)
    }

    /** Seals what a Quick Connect wait would keep, under the same Keystore key as the session's token. */
    internal val linkCipher: SecretCipher by lazy { KeystoreSecretCipher(TOKEN_KEY_ALIAS).forLibrary() }

    /** Where a Quick Connect wait is kept until it ends: one sealed record in a private file, written at once and excluded from backup. */
    internal val linkStash: LinkStash by lazy {
        SharedPreferencesLinkStash(appContext.getSharedPreferences(LINK_STASH_FILE, Context.MODE_PRIVATE))
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
        sessionsForTest?.let { return@lazy it }
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
        val fixed =
            buildMap {
                if (BuildConfig.BINGE_PROMOTION) put(SharedFlags.BingePromotion.key, true)
                if (BuildConfig.CLIENT_ENABLED) put(ClientEnabled.key, true)
            }
        RemoteFeatureFlags(
            source = FirebaseRemoteFlagSource(appContext, RemoteFlagKeys),
            overrides = StaticFlagOverrides(fixed),
            scope = scope,
            io = io,
            initialOverrides = fixed,
        )
    }

    /** Whether the user wants the client surfaces, in a store of its own. On until turned off. */
    private val clientPrefs: BooleanPreference by lazy {
        clientPreference(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("client") },
            ),
        )
    }

    /** The most the user lets a play stream, on Wi-Fi and on mobile data, in a store of its own. Auto until chosen. */
    val streamingQualityPrefs: StreamingQualityPrefs by lazy {
        StreamingQualityPrefs(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("streaming_quality") },
            ),
        )
    }

    /** The user's cap for whichever connection the device is on at each mint, which the in-app player's minter is held to. */
    val streamingCap: StreamingCap by lazy { streamingQualityPrefs.cap(ConnectivityStreamingNetwork(appContext)) }

    /** Whether the app browses and plays as well as manages: the remote flag and the user's own choice, read from this one place. */
    val clientGate: PreferenceGate by lazy {
        FlagPreferenceGate(flags, ClientEnabled, clientPrefs.value, clientPrefs::set, scope)
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
    val recentSearches: LocalRecentSearches by lazy {
        LocalRecentSearches(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { appContext.preferencesDataStoreFile("recent_searches") },
            ),
        )
    }

    /**
     * The queries the user searched in the Search tab, kept on this device alone. It is the signed-in account's list: the server id it is keyed
     * by is minted once per install, so [clearAccountCaches] forgets it when the session ends. The file (`search_history`) and the key prefix
     * (`search_query`) are on-disk names: a different one would lose what the user searched.
     */
    val queryHistory: QueryHistory by lazy {
        DataStoreQueryHistory(dataStore("search_history"), serverIdentity::currentServerId, "search_query")
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
        NotificationPlanner(
            sessions,
            notificationPrefs,
            serverIdentity,
            notificationScheduler,
            notifier,
            scope,
            // A scheduling failure is reported only when it points at this app, and only with the user's consent.
            PollBreadcrumbs(FirebaseCrashBreadcrumbs(appContext)),
        )
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
            isEnabled = { flags.flags.value[SharedFlags.ReviewPrompt] },
            scope = scope,
            clock = Clock.system(),
        )
    }

    /** Consumes a tip that completed while the app was closed, in the app's scope: a tip Play does not see acknowledged is refunded. */
    fun settleTips() = settleTipsAtStart(tipBilling, scope)

    /** The tip jar's store: Google Play in a release build, a stand-in in a debug one, which is installed over adb where Play sells nothing. */
    internal val tipBilling: TipBilling by lazy { if (BuildConfig.DEBUG) DebugTipBilling() else PlayTipBilling(appContext, scope) }

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

    /** Everything the app keeps on disk about the account that was signed in: its pictures, its recent searches and the last log shared. */
    suspend fun clearAccountCaches() =
        withContext(io) {
            userPictures.clear()
            queryHistory.clear()
            clearSharedLogs(appContext.cacheDir)
        }

    val userPictures: UserPictures by lazy { UserPictures(connection, File(appContext.cacheDir, "user-pictures")) }

    /** Lives as long as the process: the event socket is shared by every Observe stream in it. */
    private val scope = CoroutineScope(SupervisorJob())

    /** [scope] for what must outlive a page: the settings search remembers a result as it opens it, and leaves at once. */
    val appScope: CoroutineScope get() = scope

    /** The dispatcher this graph's reads and writes run on. */
    val ioDispatcher: CoroutineDispatcher get() = io

    private val playerDispatchers by lazy { PlayerDispatchers(main = Dispatchers.Main, io = io, default = io) }

    private val inAppPlayer: InAppPlayer by lazy { InAppPlayer.create(appContext, playerDispatchers) }

    /** Every play in the app, from the Play button to the last report. It lives as long as the process, so a stop is still reported once the screen is gone. */
    val playbackSittings: PlaybackSittings by lazy {
        PlaybackSittings(
            scope = scope,
            player = inAppPlayer,
            minter = SignedInSourceMinter(connection, ResourceAudioWording(appContext), streamingCap),
            reporter = SignedInPlayReporter(connection),
            nextEpisodes = SignedInNextEpisodes(connection),
            newSessionId = { UUID.randomUUID().toString() },
        )
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun playerFactory() =
        PlayerViewModelFactory(
            context = appContext,
            control = playbackSittings,
            inAppPlayer = inAppPlayer,
            dispatchers = playerDispatchers,
            clock = PlayerClock { System.currentTimeMillis() },
            fallbackMemory = AudioFallbackMemory(),
        )

    private val playerViewModels by lazy { playerFactory() }

    /** The view model of the player's screen for the sitting [sessionId], kept for as long as that screen is open. */
    @androidx.annotation.OptIn(UnstableApi::class)
    fun playerViewModels(
        sessionId: String,
        title: String,
    ): ViewModelProvider.Factory = playerViewModels.forSitting(sessionId, title)

    /** Told what the session holds, so an entry that names it is redacted. A debug build says so when one had to be. */
    private val redactor: Redactor by lazy {
        Redactor(onRedaction = { if (BuildConfig.DEBUG) Log.w(REDACTOR_TAG, "A log entry held a registered literal") })
            .also { it.follow(sessions.session.map { session -> session?.toRedactionLiterals() }, scope) }
    }

    /** The app's log: held in memory and never written to disk. A debug build also forwards each redacted entry to Logcat. */
    private val ring: RingLog by lazy {
        RingLog(
            clock = Clock.system(),
            redactor = redactor,
            // No entry carries a server field, so there is no order to label. Logging one means giving the ring the saved servers here.
            serverOrder = { emptyList() },
            forward = LogcatLogger().takeIf { BuildConfig.DEBUG },
            // The frames kept from a logged throwable are the app's own and the library's, named here so the library holds no default.
            ownPackages = OWN_LOG_PACKAGES,
        )
    }

    val logger: Logger get() = ring

    /** The app's kept log, which the Diagnostics page reads. Held in memory only. */
    val diagnosticsLog: RingLog get() = ring

    val events: ServerEvents by lazy { ServerEvents(connection, index, scope, io) }

    /** Jellyfin's UDP broadcast, on this device's LAN networks. */
    val discovery: UdpDiscovery by lazy { UdpDiscovery(targets = { lanTargets(appContext, logger) }, io = io, logger = logger) }
}

/** The client name Jellyfin shows for this app, in its dashboard's devices, sessions and activity. */
internal const val CLIENT_NAME = "Ephyra"

/** The debug switches' file and the key in it that turns the per-server stores on. A shipped build never reads either. */
internal const val DEBUG_SWITCHES_FILE = "debug_switches"

/** The private file a pending Quick Connect wait is kept in. A debug build adds `.debug` to the package, so its path does too. */
internal const val LINK_STASH_FILE = "quick_connect_pending"
internal const val SERVER_REGISTRY_SWITCH = "server_registry"
internal const val SEARCH_TOGGLES_SWITCH = "search_toggles"
private const val REDACTOR_TAG = "Redactor"
