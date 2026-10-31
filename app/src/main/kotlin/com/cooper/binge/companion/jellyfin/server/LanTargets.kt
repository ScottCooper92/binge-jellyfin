package com.cooper.binge.companion.jellyfin.server

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress

private const val TAG = "JellyfinDiscovery"
private const val IPV4_BITS = 32
private const val BYTE_MASK = 0xFF

/**
 * Where to broadcast on this device: each Wi-Fi or Ethernet network that is not a VPN, at its subnet's
 * broadcast address and at 255.255.255.255. A phone running a VPN routes the default network through
 * it, which a LAN broadcast cannot cross, so each socket is bound to its network first. Binding can
 * be refused under a VPN that may not be bypassed; the probe then goes out unbound and may find nothing.
 */
fun lanTargets(context: Context): List<DiscoveryTarget> {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
    val networks = connectivity.allNetworks.filter { it.isLan(connectivity) }
    return networks.flatMap { network ->
        val bind = { socket: java.net.DatagramSocket -> bindTo(network, socket) }
        val subnets =
            connectivity
                .getLinkProperties(network)
                ?.linkAddresses
                .orEmpty()
                .mapNotNull { it.broadcastAddress() }
        (subnets + InetAddress.getByName("255.255.255.255")).distinct().map { DiscoveryTarget(it, bind) }
    }
}

private fun Network.isLan(connectivity: ConnectivityManager): Boolean {
    val capabilities = connectivity.getNetworkCapabilities(this) ?: return false
    val lan =
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    return lan && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}

private fun bindTo(
    network: Network,
    socket: java.net.DatagramSocket,
) {
    try {
        network.bindSocket(socket)
    } catch (e: IOException) {
        Log.i(TAG, "Could not bind discovery to the LAN; sending unbound", e)
    }
}

/** The IPv4 subnet's broadcast address, or null for an IPv6 address. */
internal fun LinkAddress.broadcastAddress(): InetAddress? {
    val ip = address as? Inet4Address ?: return null
    return broadcastOf(ip, prefixLength)
}

internal fun broadcastOf(
    ip: Inet4Address,
    prefixLength: Int,
): InetAddress {
    val bits = ip.address.fold(0) { acc, byte -> (acc shl Byte.SIZE_BITS) or (byte.toInt() and BYTE_MASK) }
    val hostMask = if (prefixLength >= IPV4_BITS) 0 else -1 ushr prefixLength
    val broadcast = bits or hostMask
    return InetAddress.getByAddress(
        ByteArray(Int.SIZE_BYTES) { i -> (broadcast ushr (Byte.SIZE_BITS * (Int.SIZE_BYTES - 1 - i))).toByte() },
    )
}
