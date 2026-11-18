package com.cooper.homelab.ephyra

import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.UdpDiscovery
import com.cooper.homelab.ephyra.server.me
import com.cooper.homelab.ephyra.server.noted
import com.cooper.homelab.ephyra.ui.console.consoleCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Instant

private const val ADDRESS = "https://jelly.home.example:8920"
private const val HOST = "jelly.home.example:8920"
private const val TOKEN = "t0ken-0123456789abcdef"
private const val USER = "alice-the-watcher"

/**
 * The redaction canary: whatever the signed-in session holds is registered with the redactor, a failing call is driven, and none
 * of it may be in the log or in the text shared. The failure's message quotes all of it, as a real one can.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoggingCanaryTest {
    private val redactor = Redactor()
    private val ring =
        RingLog(
            Clock { Instant.fromEpochMilliseconds(0) },
            redactor,
            serverOrder = { emptyList() },
            ownPackages = OWN_LOG_PACKAGES,
        )
    private val session =
        JellyfinSession(
            baseUrl = ADDRESS,
            serverName = "truenas",
            serverId = "server-1",
            serverVersion = "10.11.6",
            userId = "user-1",
            userName = USER,
            token = TOKEN,
        )

    private fun seedRedactor() {
        redactor.followSession(MutableStateFlow(session), CoroutineScope(UnconfinedTestDispatcher()))
    }

    private fun assertClean() {
        val shared = ring.render()
        listOf(ADDRESS, HOST, TOKEN, USER).forEach { secret ->
            assertFalse("$secret reached the log", shared.contains(secret, ignoreCase = true))
        }
    }

    @Test
    fun `a failing console call leaves no address, token or name in the log`() {
        seedRedactor()
        val failing =
            OkHttpClient
                .Builder()
                .addInterceptor(Interceptor { throw IOException("unable to reach $ADDRESS/Users?api_key=$TOKEN for $USER") })
                .build()
        val sessions = testSessionStore()
        runBlocking { sessions.save(session) }
        val connection = Connection(failing, sessions, "Ephyra", "Test", "1.0", Dispatchers.IO, ring)

        val result = runBlocking { connection.consoleCall("Canary") { _, client -> client.me() } }

        assertFalse(result.isSuccess)
        assertClean()
        assertEquals(
            "console call failed",
            ring.entries.value
                .single()
                .event,
        )
    }

    @Test
    fun `an answer that is not Jellyfin's is logged without what it said`() {
        seedRedactor()

        UdpDiscovery.parse("""{"Address":"$ADDRESS" this is not json $TOKEN $USER""", ring)

        assertClean()
        assertEquals(
            "an answer that is not a media server's",
            ring.entries.value
                .single()
                .event,
        )
    }

    @Test
    fun `a server reached by a bare name leaves the app's own words readable and still redacts the name as a word`() {
        redactor.followSession(
            MutableStateFlow(session.copy(baseUrl = "http://jellyfin", userName = "alice-the-watcher")),
            CoroutineScope(UnconfinedTestDispatcher()),
        )

        ring.noted("JellyfinSetup", "the server timed out", IOException("could not reach jellyfin"))

        val shared = ring.render()
        assertTrue(shared, shared.contains("JellyfinSetup"))
        assertTrue(shared, shared.contains("the server timed out"))
        assertTrue(shared, shared.contains("IOException"))
        assertEquals("a [redacted] b", redactor.redact("a jellyfin b"))
        assertEquals("a [redacted]/Users b", redactor.redact("a http://jellyfin/Users b"))
    }

    @Test
    fun `a failure's class name keeps this app's own package when the host is named jellyfin`() {
        redactor.followSession(
            MutableStateFlow(session.copy(baseUrl = "http://jellyfin")),
            CoroutineScope(UnconfinedTestDispatcher()),
        )

        // The package has `jellyfin` as a segment, and a dot is a word boundary: the class is a constant of this app, not the server's.
        ring.noted("JellyfinSetup", "the server answered", JellyfinHttpException(502, "/System/Info/Public"))

        val shared = ring.render()
        assertTrue(shared, shared.contains("com.cooper.homelab.ephyra.server.JellyfinHttpException"))
        assertFalse(shared, shared.contains("companion.[redacted]"))
        // The bare name is still redacted wherever it stands as a word, so nothing the server's name reached survives.
        assertEquals("a [redacted] b", redactor.redact("a jellyfin b"))
    }

    @Test
    fun `the session's literals follow it when it changes and go when it is cleared`() {
        val sessions = MutableStateFlow<JellyfinSession?>(session)
        redactor.followSession(sessions, CoroutineScope(UnconfinedTestDispatcher()))
        assertEquals("a [redacted] b [redacted] c", redactor.redact("a $HOST b $TOKEN c"))

        sessions.value = session.copy(baseUrl = "https://other.example", token = "second-token-1", userName = "bob-the-reader")
        assertEquals("a $HOST b $TOKEN c", redactor.redact("a $HOST b $TOKEN c"))
        assertEquals("[redacted] [redacted] [redacted]", redactor.redact("https://other.example second-token-1 bob-the-reader"))

        sessions.value = null
        assertEquals("https://other.example", redactor.redact("https://other.example"))
    }
}
