package com.cooper.homelab.ephyra.server

import com.cooper.homelab.core.server.connection.ConnectionHealth
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
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

/** What the saved server's calls say about whether it is answering, as the shared health monitor reads them through this app's client. */
class ConnectionHealthWiringTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private lateinit var connection: Connection

    @Before
    fun signedIn() {
        runBlocking { sessions.save(jellyfin.session()) }
        // Built after the session is saved, and waited on until it is watching, so the watcher's baseline is the saved session and
        // no later "change" resets the monitor in the middle of a test.
        connection = Connection(OkHttpClient(), sessions, "Ephyra", "Test", "1.0", Dispatchers.IO, DiscardLogger)
        runBlocking { withTimeout(WAIT_MILLIS) { connection.awaitWatching() } }
    }

    @After
    fun close() {
        connection.close()
        jellyfin.close()
    }

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
    fun `calls to the saved server feed the monitor, and a dropped one is no answer`() {
        call()
        assertEquals(ConnectionHealth.Healthy, connection.monitor.current(SAVED_SERVER))
        jellyfin.dropped += "/Users/Me"
        call()
        assertEquals(ConnectionHealth.Healthy, connection.monitor.current(SAVED_SERVER))
        call()
        assertEquals(ConnectionHealth.Unreachable, connection.monitor.current(SAVED_SERVER))
        jellyfin.dropped -= "/Users/Me"
        call()
        assertEquals(ConnectionHealth.Healthy, connection.monitor.current(SAVED_SERVER))
    }

    @Test
    fun `a gateway that says the server behind it is down is no answer, and any other error is one`() {
        for (code in listOf(502, 503, 504)) {
            val monitor = connection.monitor
            monitor.reset(SAVED_SERVER)
            jellyfin.status["/Users/Me"] = code
            call()
            call()
            assertEquals("$code", ConnectionHealth.Unreachable, monitor.current(SAVED_SERVER))
        }
        for (code in listOf(400, 403, 404, 429, 500)) {
            connection.monitor.reset(SAVED_SERVER)
            jellyfin.status["/Users/Me"] = code
            call()
            call()
            assertEquals("$code", ConnectionHealth.Healthy, connection.monitor.current(SAVED_SERVER))
        }
    }

    @Test
    fun `an address being tried is not the saved server, so its calls do not feed the monitor`() {
        jellyfin.dropped += "/System/Info/Public"
        runBlocking {
            val anonymous = connection.anonymous(jellyfin.baseUrl.toHttpUrl())
            repeat(2) { runCatching { anonymous.me() } }
        }
        assertEquals(ConnectionHealth.Unchecked, connection.monitor.current(SAVED_SERVER))
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
        assertEquals(ConnectionHealth.Unchecked, connection.monitor.current(SAVED_SERVER))
    }

    @Test
    fun `a new session starts the monitor again`() {
        runBlocking { withTimeout(WAIT_MILLIS) { connection.awaitWatching() } }
        call()
        assertEquals(ConnectionHealth.Healthy, connection.monitor.current(SAVED_SERVER))
        runBlocking {
            sessions.save(jellyfin.session().copy(token = "another-token"))
            withTimeout(WAIT_MILLIS) { while (connection.monitor.current(SAVED_SERVER) != ConnectionHealth.Unchecked) delay(POLL_MILLIS) }
        }
        assertEquals(ConnectionHealth.Unchecked, connection.monitor.current(SAVED_SERVER))
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 10L
        const val SETTLE_MILLIS = 500L
    }
}
