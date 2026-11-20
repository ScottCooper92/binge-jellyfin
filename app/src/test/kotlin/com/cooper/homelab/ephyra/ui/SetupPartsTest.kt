package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.AddressPolicy
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinUnreadableAnswer
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.telemetry.SignInReports
import com.cooper.homelab.ephyra.testSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** The parts the setup half is made of, each on its own: what the shared sign-in flow's plug-ins will be. */
@RunWith(RobolectricTestRunner::class)
class SetupPartsTest {
    private val jellyfin = FakeJellyfin()
    private val teardown = ViewModelTeardown()
    private val sessions = testSessionStore()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private val reports =
        SignInReports(
            object : Analytics {
                override fun event(
                    name: String,
                    properties: Map<String, Any>,
                ) {
                    events += name to properties
                }

                override fun screen(name: String) = Unit
            },
        )

    @After
    fun close() = teardown.close(jellyfin)

    private val signedIn = SignedIn(token = "token-1", userId = "user-1", userName = "Claude", serverId = "server-1")
    private val server = PublicServerInfo("truenas", "10.11.6", "server-1", "http://truenas.local:8096/".toHttpUrl())

    private fun quickConnect(window: Long = 10_000L) = QuickConnectSignIn(connection, pollMillis = 1L, windowMillis = window)

    @Test
    fun `Quick Connect hands out the code and ends approved with the session the server minted`() {
        jellyfin.quickConnectApproved = true
        val codes = mutableListOf<String>()
        val outcome = runBlocking { quickConnect().run(jellyfin.baseUrl.toHttpUrl()) { codes += it } }
        assertEquals(listOf("123456"), codes)
        assertEquals("token-1", (outcome as QuickConnectSignIn.Outcome.Approved).signedIn.token)
    }

    @Test
    fun `Quick Connect turned off asks for no code`() {
        jellyfin.quickConnectEnabled = false
        val codes = mutableListOf<String>()
        val outcome = runBlocking { quickConnect().run(jellyfin.baseUrl.toHttpUrl()) { codes += it } }
        assertEquals(QuickConnectSignIn.Outcome.Off, outcome)
        assertTrue(codes.isEmpty())
    }

    @Test
    fun `a code nobody approves expires with the window`() {
        val outcome = runBlocking { quickConnect(window = 20L).run(jellyfin.baseUrl.toHttpUrl()) { } }
        assertEquals(QuickConnectSignIn.Outcome.Expired, outcome)
    }

    private fun failureOf(
        refused: (JellyfinHttpException) -> SetupError = { SetupError.WrongCredentials },
        block: suspend () -> Unit,
    ): SetupError? {
        var seen: SetupError? = null
        runBlocking {
            connection.setupCall("a call", "http://truenas.local:8096", LocalNetworkPermission.AlwaysGranted, refused, { seen = it }, block)
        }
        return seen
    }

    @Test
    fun `a refusal is worded by the caller, an unreadable answer is generic, and no answer is unreachable`() {
        assertEquals(SetupError.WrongCredentials, failureOf { throw JellyfinHttpException(401, "/Users") })
        assertEquals(SetupError.Generic, failureOf { throw JellyfinUnreadableAnswer("/Users") })
        assertEquals(SetupError.Unreachable, failureOf { throw IOException("no route") })
        assertNull(failureOf { })
    }

    @Test
    fun `a cancelled scope is not a failure`() {
        var failed = false
        try {
            runBlocking {
                connection.setupCall(
                    "a call",
                    "http://truenas.local:8096",
                    LocalNetworkPermission.AlwaysGranted,
                    { SetupError.Generic },
                    { failed = true },
                ) { throw CancellationException("stopped") }
            }
            fail("the cancellation was swallowed")
        } catch (_: CancellationException) {
            assertTrue(!failed)
        }
    }

    @Test
    fun `a kept session begins the signed-in state, drops the last account's index and counts the sign-in`() {
        val handoff = SessionHandoff(sessions, LibraryIndex(), reports, AddressPolicy.Legacy)
        val begun = runBlocking { handoff.save("http://truenas.local:8096/".toHttpUrl(), server, signedIn, "password") }
        assertEquals("http://truenas.local:8096", begun?.session?.baseUrl)
        assertEquals("token-1", begun?.session?.token)
        assertEquals("sign_in", events.single().first)
        assertNotNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a session the store refuses begins nothing and counts the failure`() {
        val refusing =
            object : SessionStore by sessions {
                override suspend fun save(session: JellyfinSession) = false
            }
        val handoff = SessionHandoff(refusing, LibraryIndex(), reports, AddressPolicy.Legacy)
        val begun = runBlocking { handoff.save("http://truenas.local:8096/".toHttpUrl(), server, signedIn, "password") }
        assertNull(begun)
        assertEquals("sign_in_failed", events.single().first)
        assertNull(runBlocking { sessions.current() })
    }
}
