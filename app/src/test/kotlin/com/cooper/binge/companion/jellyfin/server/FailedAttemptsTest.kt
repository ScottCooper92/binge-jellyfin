package com.cooper.binge.companion.jellyfin.server

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy

/** A call can fail more than once on the way to its answer, and the caller hears of each failure. */
class FailedAttemptsTest {
    private val heard = mutableListOf<IOException>()
    private val listener = FailedAttempts { heard += it }
    private val call = OkHttpClient().newCall(Request.Builder().url("https://jellyfin.example.com/").build())

    @Test
    fun `a failure in the connection the request or the response or the whole call is passed on in order`() {
        val connect = IOException("connect")
        val request = IOException("request")
        val response = IOException("response")
        val whole = IOException("call")

        listener.connectFailed(call, InetSocketAddress.createUnresolved("jellyfin.example.com", 443), Proxy.NO_PROXY, null, connect)
        listener.requestFailed(call, request)
        listener.responseFailed(call, response)
        listener.callFailed(call, whole)

        assertEquals(listOf(connect, request, response, whole), heard)
    }
}
