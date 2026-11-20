package com.cooper.homelab.ephyra.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.ephyra.playback.StreamingNetwork
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** Which connection the device is on, from the system's active network: cellular or metered is mobile data, the rest is Wi-Fi. */
@RunWith(RobolectricTestRunner::class)
class ConnectivityStreamingNetworkTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private fun onNetwork(
        transport: Int,
        metered: Boolean,
    ) {
        val network = ShadowNetwork.newInstance(1)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addTransportType(transport)
        if (!metered) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val shadow = shadowOf(connectivity)
        shadow.addNetwork(network, ShadowNetworkInfo.newInstance(null, ConnectivityManager.TYPE_WIFI, 0, true, true))
        shadow.setNetworkCapabilities(network, capabilities)
        shadow.setActiveNetworkInfo(connectivity.getNetworkInfo(network))
    }

    @Test
    fun `an unmetered Wi-Fi is Wi-Fi`() {
        onNetwork(NetworkCapabilities.TRANSPORT_WIFI, metered = false)
        assertEquals(StreamingNetwork.Wifi, ConnectivityStreamingNetwork(context).current())
    }

    @Test
    fun `Ethernet is Wi-Fi`() {
        onNetwork(NetworkCapabilities.TRANSPORT_ETHERNET, metered = false)
        assertEquals(StreamingNetwork.Wifi, ConnectivityStreamingNetwork(context).current())
    }

    @Test
    fun `a cellular network is mobile data`() {
        onNetwork(NetworkCapabilities.TRANSPORT_CELLULAR, metered = true)
        assertEquals(StreamingNetwork.Mobile, ConnectivityStreamingNetwork(context).current())
    }

    @Test
    fun `a metered Wi-Fi, such as a phone's hotspot, is mobile data`() {
        onNetwork(NetworkCapabilities.TRANSPORT_WIFI, metered = true)
        assertEquals(StreamingNetwork.Mobile, ConnectivityStreamingNetwork(context).current())
    }

    @Test
    fun `no active network is Wi-Fi`() {
        shadowOf(connectivity).setActiveNetworkInfo(null)
        assertEquals(StreamingNetwork.Wifi, ConnectivityStreamingNetwork(context).current())
    }
}
