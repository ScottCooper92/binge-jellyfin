package com.cooper.binge.companion.jellyfin.flags

import android.content.Context
import com.binge.companion.sdk.attempt
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Where flag values come from, as far as [RemoteFeatureFlags] is concerned. */
interface RemoteFlagSource {
    /** The last values this device activated, every flag off where it has none. */
    fun current(): Flags

    /** Fetches and activates, then returns the new values; null when nothing could be fetched. */
    suspend fun fetch(): Flags?
}

/**
 * Firebase Remote Config, in this app's own project (`binge-jellyfin-companion`). A build without its
 * `google-services.json` has no Firebase app, and there every flag stays off rather than failing.
 * Remote Config keeps the last activated values on disk, so a cold start reads them before the first
 * fetch lands. The same shape as binge-seerr's.
 */
class FirebaseRemoteFlagSource(
    private val context: Context,
) : RemoteFlagSource {
    private val config: FirebaseRemoteConfig? by lazy {
        if (FirebaseApp.getApps(context).isEmpty()) return@lazy null
        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = MIN_FETCH_SECONDS })
            setDefaultsAsync(Flag.entries.associate { it.key to false })
        }
    }

    override fun current(): Flags = config?.let(::read) ?: Flags()

    override suspend fun fetch(): Flags? {
        val remote = config ?: return null
        val fetched =
            suspendCancellableCoroutine { continuation ->
                attempt { remote.fetchAndActivate() }
                    .onSuccess { task -> task.addOnCompleteListener { continuation.resume(it.isSuccessful) } }
                    .onFailure { continuation.resume(false) }
            }
        return if (fetched) read(remote) else null
    }

    private fun read(remote: FirebaseRemoteConfig) = Flags(bingePromotion = remote.getBoolean(Flag.BingePromotion.key))

    private companion object {
        const val MIN_FETCH_SECONDS = 3_600L
    }
}
