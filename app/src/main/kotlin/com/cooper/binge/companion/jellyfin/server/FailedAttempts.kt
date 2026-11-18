package com.cooper.binge.companion.jellyfin.server

import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/** Passes on each failure a call meets, in the connection, the request or the response, not only the one it ends with. */
internal class FailedAttempts(
    private val onFailure: (IOException) -> Unit,
) : EventListener() {
    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) = onFailure(ioe)

    override fun requestFailed(
        call: Call,
        ioe: IOException,
    ) = onFailure(ioe)

    override fun responseFailed(
        call: Call,
        ioe: IOException,
    ) = onFailure(ioe)

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) = onFailure(ioe)
}
