package com.cooper.binge.companion.jellyfin

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The fake's event sockets and its shutdown. A test can end while the app's socket handshake is still in flight, so a
 * socket can register after the fake took its snapshot of the open ones; an open socket holds the server's shutdown.
 */
class FakeJellyfinSocketTest {
    private val jellyfin = FakeJellyfin()
    private val http = OkHttpClient()

    @After
    fun close() = jellyfin.close()

    private class Closure : WebSocketListener() {
        val closed = CountDownLatch(1)
        val code = AtomicInteger(-1)

        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
        ) {
            this.code.set(code)
            closed.countDown()
            webSocket.close(NORMAL, null)
        }

        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
        ) = closed.countDown()
    }

    private fun openSocket(listener: WebSocketListener) =
        http.newWebSocket(
            Request
                .Builder()
                .url("${jellyfin.baseUrl}/socket")
                .header("Authorization", "MediaBrowser Token=\"token-1\"")
                .build(),
            listener,
        )

    @Test
    fun `a socket that opens after the sockets were closed is closed at once and never held`() {
        jellyfin.closeSockets()
        val late = Closure()

        openSocket(late)

        assertTrue(late.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(GOING_AWAY, late.code.get())
        assertTrue(jellyfin.sockets.isEmpty())
    }

    @Test
    fun `a socket open before the sockets were closed is closed with them`() {
        val early = Closure()
        openSocket(early)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (jellyfin.sockets.isEmpty() && System.nanoTime() < deadline) Thread.sleep(POLL_MILLIS)
        assertTrue(jellyfin.sockets.isNotEmpty())

        jellyfin.closeSockets()

        assertTrue(early.closed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(GOING_AWAY, early.code.get())
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
        const val POLL_MILLIS = 20L
        const val GOING_AWAY = 1001
        const val NORMAL = 1000
    }
}
