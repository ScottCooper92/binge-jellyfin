package com.cooper.homelab.ephyra.server

import com.cooper.homelab.ephyra.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Waiting for the saves in flight, while they finish. */
@RunWith(RobolectricTestRunner::class)
class ConnectionAwaitSavesTest {
    private val connection =
        Connection(OkHttpClient(), testSessionStore(), "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)

    @After
    fun closed() = connection.close()

    @Test
    fun `waiting for saves that finish as it looks does not fail`() {
        // One save in flight is the case that failed: its size read as one, and it was gone before its element was.
        repeat(ROUNDS) {
            connection.launchSave { }
            runBlocking { withTimeout(TIMEOUT_MILLIS) { connection.awaitSaves() } }
        }
    }

    private companion object {
        const val ROUNDS = 5_000
        const val TIMEOUT_MILLIS = 60_000L
    }
}
