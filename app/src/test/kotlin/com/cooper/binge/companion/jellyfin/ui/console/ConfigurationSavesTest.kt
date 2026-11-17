package com.cooper.binge.companion.jellyfin.ui.console

import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscardLogger
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** The save engine under every settings page, over the shared library's rules for what a failed write means. */
@RunWith(RobolectricTestRunner::class)
class ConfigurationSavesTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** What the server was sent, and how it answers the next write: null is success, else the exception it fails with. */
    private val sent = CopyOnWriteArrayList<JsonObject>()
    private var answer: Throwable? = null
    private val shown = CopyOnWriteArrayList<Pair<JsonObject, ConsoleFailure?>>()
    private val progress = CopyOnWriteArrayList<SaveProgress>()

    private val saves =
        ConfigurationSaves(
            connection = connection,
            scope = scope,
            tag = "Test",
            save = { _, _, configuration ->
                sent += configuration
                answer?.let { throw it }
            },
            onProgress = { progress += it },
            onShown = { configuration, failure -> shown += configuration to failure },
        )

    @Before
    fun signIn() {
        runBlocking { sessions.save(jellyfin.session()) }
        saves.loaded(JsonObject(mapOf("a" to JsonPrimitive(1))))
    }

    @After
    fun close() {
        scope.cancel()
        connection.close()
        jellyfin.close()
    }

    private suspend fun until(check: () -> Boolean) = withTimeout(TIMEOUT_MILLIS) { while (!check()) delay(POLL_MILLIS) }

    private fun change(value: Int) = saves.set("a", JsonPrimitive(value))

    @Test
    fun `a change is written whole and the page says saved`() =
        runBlocking {
            change(2)

            until { progress.lastOrNull()?.status == SaveStatus.Saved }

            assertEquals(listOf(JsonObject(mapOf("a" to JsonPrimitive(2)))), sent.toList())
            assertEquals(false, progress.last().failed)
        }

    @Test
    fun `a write that fails with a server error keeps the change unsent and a retry sends it`() =
        runBlocking {
            answer = JellyfinHttpException(503, "/System/Configuration")
            change(2)
            until { progress.lastOrNull()?.failed == true }

            // Kept on screen, said to be unsent, and not put back: the server was down, not saying no.
            assertEquals(JsonPrimitive(2), saves.shown["a"])
            assertEquals(ConsoleFailure.Unreachable, shown.last().second)

            answer = null
            saves.retry()
            until { progress.lastOrNull()?.status == SaveStatus.Saved }
            assertEquals(false, progress.last().failed)
            assertEquals(JsonPrimitive(2), sent.last()["a"])
        }

    @Test
    fun `a write that cannot reach the server is kept unsent too`() =
        runBlocking {
            answer = IOException("no route")
            change(2)

            until { progress.lastOrNull()?.failed == true }

            assertEquals(JsonPrimitive(2), saves.shown["a"])
        }

    @Test
    fun `a write the server refuses puts the setting back and says why`() =
        runBlocking {
            answer = JellyfinHttpException(409, "/System/Configuration", "Not allowed")
            change(2)

            until { shown.any { it.second != null } }

            assertEquals(JsonPrimitive(1), saves.shown["a"])
            assertEquals(ConsoleFailure.Refused, shown.last().second)
            assertEquals(false, progress.last().failed)
        }

    @Test
    fun `leaving the page sends the change still waiting and the job completes once the server has it`() =
        runBlocking {
            change(2)

            saves.cleared()

            until { sent.isNotEmpty() }
            connection.awaitSaves()
            assertEquals(JsonPrimitive(2), sent.last()["a"])
        }

    @Test
    fun `settling sends what is waiting and answers nothing when it landed`() =
        runBlocking {
            change(2)

            assertNull(saves.settle().await())

            assertEquals(JsonPrimitive(2), sent.last()["a"])
        }

    @Test
    fun `settling a write the server refuses tells the page and answers nothing`() =
        runBlocking {
            answer = JellyfinHttpException(409, "/System/Configuration")
            change(2)

            assertNull(saves.settle().await())

            until { shown.any { it.second == ConsoleFailure.Refused } }
            assertTrue(saves.shown["a"] == JsonPrimitive(1))
        }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
