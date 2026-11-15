package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** What the saved server's calls say about whether it is answering, the way binge-seerr's connection health reads them. */
class ConnectionMonitorTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Ephyra", "Test", "1.0", Dispatchers.IO)

    @Before
    fun signedIn() {
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = jellyfin.close()

    /** One call to the saved server, which may fail: only what it tells the monitor is looked at. */
    private fun call() {
        runBlocking {
            val (_, client) = checkNotNull(connection.signedIn())
            try {
                client.me()
            } catch (_: Exception) {
                // The failure is what the test sets up; the monitor is what it reads.
            }
        }
    }

    @Test
    fun `a single call without an answer is a blip, and a run of them is an unreached server`() {
        val monitor = ConnectionMonitor()
        assertEquals(Reach.Unchecked, monitor.reach.value)
        monitor.reportSilent()
        assertEquals(Reach.Unchecked, monitor.reach.value)
        monitor.reportSilent()
        assertEquals(Reach.Unreached, monitor.reach.value)
    }

    @Test
    fun `any answer reads as reached again, and a blip after it needs its streak from the start`() {
        val monitor = ConnectionMonitor()
        monitor.reportSilent()
        monitor.reportSilent()
        monitor.reportAnswered()
        assertEquals(Reach.Reached, monitor.reach.value)
        monitor.reportSilent()
        assertEquals(Reach.Reached, monitor.reach.value)
        monitor.reportSilent()
        assertEquals(Reach.Unreached, monitor.reach.value)
        monitor.reset()
        assertEquals(Reach.Unchecked, monitor.reach.value)
    }

    @Test
    fun `calls to the saved server feed the monitor, and a dropped one is no answer`() {
        call()
        assertEquals(Reach.Reached, connection.monitor.reach.value)
        jellyfin.dropped += "/Users/Me"
        call()
        assertEquals(Reach.Reached, connection.monitor.reach.value)
        call()
        assertEquals(Reach.Unreached, connection.monitor.reach.value)
        jellyfin.dropped -= "/Users/Me"
        call()
        assertEquals(Reach.Reached, connection.monitor.reach.value)
    }

    @Test
    fun `a gateway that says the server behind it is down is no answer, and any other error is one`() {
        for (code in listOf(502, 503, 504)) {
            val monitor = connection.monitor
            monitor.reset()
            jellyfin.status["/Users/Me"] = code
            call()
            call()
            assertEquals("$code", Reach.Unreached, monitor.reach.value)
        }
        for (code in listOf(400, 403, 404, 429, 500)) {
            connection.monitor.reset()
            jellyfin.status["/Users/Me"] = code
            call()
            call()
            assertEquals("$code", Reach.Reached, connection.monitor.reach.value)
        }
    }

    @Test
    fun `an address being tried is not the saved server, so its calls do not feed the monitor`() {
        jellyfin.dropped += "/System/Info/Public"
        runBlocking {
            val anonymous = connection.anonymous(jellyfin.baseUrl.toHttpUrl())
            repeat(2) { runCatching { anonymous.me() } }
        }
        assertEquals(Reach.Unchecked, connection.monitor.reach.value)
    }

    @Test
    fun `calls cancelled in flight say nothing about the server`() {
        val first = jellyfin.hold("/Users/Me")
        val second = jellyfin.hold("/Users/Me")
        runBlocking {
            val (_, client) = checkNotNull(connection.signedIn())
            val calls = launch(Dispatchers.IO) { listOf(async { client.me() }, async { client.me() }).forEach { it.await() } }
            first.awaitArrival()
            second.awaitArrival()
            calls.cancelAndJoin()
            // The cancelled calls fail on OkHttp's threads, a moment after the join.
            delay(SETTLE_MILLIS)
        }
        first.release()
        second.release()
        assertEquals(Reach.Unchecked, connection.monitor.reach.value)
    }

    @Test
    fun `a new session starts the monitor again`() {
        call()
        assertEquals(Reach.Reached, connection.monitor.reach.value)
        runBlocking {
            sessions.save(jellyfin.session().copy(token = "another-token"))
            withTimeout(WAIT_MILLIS) { while (connection.monitor.reach.value != Reach.Unchecked) delay(POLL_MILLIS) }
        }
        assertEquals(Reach.Unchecked, connection.monitor.reach.value)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
        const val SETTLE_MILLIS = 500L
    }
}
