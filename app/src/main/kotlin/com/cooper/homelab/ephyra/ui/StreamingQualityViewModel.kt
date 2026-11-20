package com.cooper.homelab.ephyra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.homelab.ephyra.playback.StreamingNetwork
import com.cooper.homelab.ephyra.playback.StreamingQualityPrefs
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** The most the user lets a play stream on each kind of connection, in whole Mbps; 0 is Auto. */
data class StreamingQualitySettings(
    val wifiMbps: Int,
    val mobileMbps: Int,
)

/** The device's own streaming quality choices, read from and saved to its preferences as they are made. Null until they are read. */
class StreamingQualityViewModel(
    private val prefs: StreamingQualityPrefs,
) : ViewModel() {
    val settings: StateFlow<StreamingQualitySettings?> =
        combine(prefs.maxMbps(StreamingNetwork.Wifi), prefs.maxMbps(StreamingNetwork.Mobile), ::StreamingQualitySettings)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun set(
        network: StreamingNetwork,
        mbps: Int,
    ) {
        // A write the page is left during still lands: it is the user's choice, and a few bytes.
        viewModelScope.launch { withContext(NonCancellable) { prefs.setMaxMbps(network, mbps) } }
    }
}
