package com.cooper.homelab.ephyra.server

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The session watcher a [Connection] starts on its own, which ends a save made under a session that has since changed (#165, #502). */
@RunWith(RobolectricTestRunner::class)
class ConnectionWatcherTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private lateinit var connection: Connection

    @Before
    fun saved() {
        runBlocking { sessions.save(jellyfin.session()) }
        connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)
        runBlocking { withTimeout(TIMEOUT_MILLIS) { connection.awaitWatching() } }
    }

    @After
    fun closed() {
        connection.close()
        jellyfin.close()
    }

    private fun running(): Pair<CompletableDeferred<Unit>, CompletableDeferred<Unit>> {
        val started = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        connection.launchSave {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                ended.complete(Unit)
            }
        }
        runBlocking { withTimeout(TIMEOUT_MILLIS) { started.await() } }
        return started to ended
    }

    @Test
    fun `a save in flight ends when the saved session changes`() {
        val (_, ended) = running()
        runBlocking { sessions.clear() }
        runBlocking { withTimeout(TIMEOUT_MILLIS) { ended.await() } }
    }

    @Test
    fun `a save in flight is left alone while the session stays the same`() {
        val (_, ended) = running()
        runBlocking { sessions.save(jellyfin.session()) }
        // Saving the same session again changes nothing the watcher measures, so the save must still be running.
        assertTrue(!ended.isCompleted)
    }

    @Test
    fun `closing the connection ends its saves and its watcher`() {
        val (_, ended) = running()
        connection.close()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { ended.await() } }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
