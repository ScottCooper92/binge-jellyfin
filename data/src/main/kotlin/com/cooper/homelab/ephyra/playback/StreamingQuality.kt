package com.cooper.homelab.ephyra.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val BITS_PER_MEGABIT = 1_000_000L

/** The two kinds of connection the user sets a quality for. */
enum class StreamingNetwork { Wifi, Mobile }

/** Which kind of connection the device is on now. The one implementation reads the system's, so no model or view model sees it. */
fun interface StreamingNetworkSource {
    fun current(): StreamingNetwork
}

/** The most the user lets a play stream, in bits a second, or null for no limit of their own. Read at each mint, so a change applies from the next one. */
fun interface StreamingCap {
    suspend fun maxBitrateBps(): Long?
}

/** No cap of the user's own: the device's profile stands. */
val NoStreamingCap = StreamingCap { null }

/** What the picker offers, in Mbps, most first. 0 is Auto: no cap of the user's own. */
val STREAMING_QUALITY_STEPS_MBPS: List<Int> = listOf(0, 40, 20, 10, 8, 4, 2, 1)

/**
 * The most the user lets a play stream, on Wi-Fi and on mobile data, kept on this device (it is the device's connection, not the
 * account's, so it stays across sign-outs). Two whole-megabit keys in the app's own preferences file; an absent key is Auto, and
 * nothing is written until the user chooses. The key names are on disk: change one and every saved choice reads as Auto.
 */
class StreamingQualityPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    /** The cap on [network] in Mbps, 0 for Auto. */
    fun maxMbps(network: StreamingNetwork): Flow<Int> = dataStore.data.map { it[key(network)] ?: 0 }.distinctUntilChanged()

    /** Records the cap on [network]; 0 or less is Auto, which removes the key. */
    suspend fun setMaxMbps(
        network: StreamingNetwork,
        mbps: Int,
    ) {
        dataStore.edit { if (mbps <= 0) it.remove(key(network)) else it[key(network)] = mbps }
    }

    /** The cap for whichever connection [network] says the device is on now. */
    fun cap(network: StreamingNetworkSource): StreamingCap =
        StreamingCap { maxMbps(network.current()).first().takeIf { it > 0 }?.let { it * BITS_PER_MEGABIT } }

    private fun key(network: StreamingNetwork) =
        intPreferencesKey(
            when (network) {
                StreamingNetwork.Wifi -> "max_bitrate_wifi_mbps"
                StreamingNetwork.Mobile -> "max_bitrate_mobile_mbps"
            },
        )
}

/** The profile with its bitrate ceiling at [max] where that is lower than the device's own: a cap never raises what the device can play. */
internal fun PlaybackDeviceProfile.cappedTo(max: Long): PlaybackDeviceProfile =
    copy(maxBitrateBps = if (maxBitrateBps > 0) minOf(maxBitrateBps, max) else max)
