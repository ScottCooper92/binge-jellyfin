package com.cooper.homelab.ephyra.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cooper.homelab.ephyra.playback.StreamingNetwork
import com.cooper.homelab.ephyra.playback.StreamingNetworkSource

/**
 * Whether the device is on mobile data, read from the system's active network. A network that is cellular or metered counts as mobile
 * data, so a phone's hotspot or a metered Wi-Fi is limited as mobile data is; Wi-Fi, Ethernet and a VPN over them count as Wi-Fi. With no
 * active network it says Wi-Fi: nothing could be minted anyway.
 */
class ConnectivityStreamingNetwork(
    context: Context,
) : StreamingNetworkSource {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override fun current(): StreamingNetwork {
        val capabilities = connectivity?.activeNetwork?.let(connectivity::getNetworkCapabilities) ?: return StreamingNetwork.Wifi
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            metered
        ) {
            StreamingNetwork.Mobile
        } else {
            StreamingNetwork.Wifi
        }
    }
}
