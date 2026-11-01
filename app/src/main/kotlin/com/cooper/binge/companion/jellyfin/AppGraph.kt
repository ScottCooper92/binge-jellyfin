package com.cooper.binge.companion.jellyfin

import android.content.Context
import android.os.Build
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.cooper.binge.companion.jellyfin.auth.KeystoreSecretCipher
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.flags.FeatureFlags
import com.cooper.binge.companion.jellyfin.flags.FixedFeatureFlags
import com.cooper.binge.companion.jellyfin.flags.Flags
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.ServerEvents
import com.cooper.binge.companion.jellyfin.server.UdpDiscovery
import com.cooper.binge.companion.jellyfin.server.lanTargets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** The app's object graph, built by hand. One of each for the life of the process. */
class AppGraph(
    context: Context,
    /** Where every server call runs. */
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

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
            KeystoreSecretCipher(),
            io,
        )
    }

    /** Whether the app names Binge: the build decides, until there is a flag service (see [FeatureFlags]). */
    val flags: FeatureFlags = FixedFeatureFlags(Flags(bingePromotion = BuildConfig.BINGE_PROMOTION))

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

    /** Lives as long as the process: the event socket is shared by every Observe stream in it. */
    private val scope = CoroutineScope(SupervisorJob())

    val events: ServerEvents by lazy { ServerEvents(connection, index, scope, io) }

    /** Jellyfin's UDP broadcast, on this device's LAN networks. */
    val discovery: UdpDiscovery by lazy { UdpDiscovery(targets = { lanTargets(appContext) }, io = io) }
}

/** The client name Jellyfin shows for this app, in its dashboard's devices, sessions and activity. */
internal const val CLIENT_NAME = "Jellyfin Companion"
