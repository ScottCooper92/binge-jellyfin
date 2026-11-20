package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.LinkExpiredException
import com.cooper.homelab.core.presentation.connect.LinkNotOfferedException
import com.cooper.homelab.core.presentation.connect.NotThisServerException
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.PublicServerInfo
import com.cooper.homelab.ephyra.server.SignedIn
import com.cooper.homelab.ephyra.testSessionStore
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
import kotlin.time.Duration.Companion.milliseconds

/** The parts the connect flow is plugged in with, each on its own: the probe, Quick Connect, the sink, and the projection to the screens. */
@RunWith(RobolectricTestRunner::class)
class SetupPartsTest {
    private val jellyfin = FakeJellyfin()
    private val teardown = ViewModelTeardown()
    private val sessions = testSessionStore()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    @After
    fun close() = teardown.close(jellyfin)

    private val signedIn = SignedIn(token = "token-1", userId = "user-1", userName = "Claude", serverId = "server-1")
    private val info = PublicServerInfo("truenas", "10.11.6", "server-1", "http://truenas.local:8096/".toHttpUrl())

    private fun address(url: String = jellyfin.baseUrl) = ServerAddress.parse(url) ?: error("not an address: $url")

    private fun server(preview: PublicServerInfo = info) =
        ProbedServer(address(preview.baseUrl.toString()), preview.serverName, !preview.setupIncomplete, listOf(SetupMode.Password), preview)

    @Test
    fun `the probe reads who the server is and offers Quick Connect while it is on`() {
        val probed = runBlocking { JellyfinProbe(connection).probe(address()) }
        assertEquals("truenas", probed.name)
        assertTrue(probed.setUp)
        assertEquals(listOf<Any>(SetupMode.Password, SetupMode.QuickConnect), probed.modes)
    }

    @Test
    fun `the probe leaves Quick Connect out when the server has it off`() {
        jellyfin.quickConnectEnabled = false
        val probed = runBlocking { JellyfinProbe(connection).probe(address()) }
        assertEquals(listOf<Any>(SetupMode.Password), probed.modes)
    }

    @Test
    fun `an address that answers but not as Jellyfin is not this server`() {
        jellyfin.status["/System/Info/Public"] = 404
        try {
            runBlocking { JellyfinProbe(connection).probe(address()) }
            fail("the probe accepted it")
        } catch (_: NotThisServerException) {
            // expected
        }
    }

    @Test
    fun `a failure is classified by what it says, with an unreadable answer apart from silence`() {
        runBlocking {
            assertEquals(
                ServerFailure.Unreadable,
                com.cooper.homelab.ephyra.server
                    .JellyfinUnreadableAnswer("/x")
                    .setupFailure(),
            )
            assertEquals(
                ServerFailure.SessionRejected,
                com.cooper.homelab.ephyra.server
                    .JellyfinHttpException(401, "/x")
                    .setupFailure(),
            )
            assertTrue(java.io.IOException("no route").setupFailure() is ServerFailure.Unreachable)
        }
    }

    @Test
    fun `Quick Connect gives a code and the session the server minted once the user approves`() {
        jellyfin.quickConnectApproved = true
        val provider = JellyfinQuickConnect(connection, pollMillis = 1L)
        val probed = runBlocking { JellyfinProbe(connection).probe(address()) }
        val link = runBlocking { provider.start(probed, forTelevision = false) }
        assertEquals("123456", link.code)
        assertEquals("token-1", runBlocking { provider.await(probed, link) }.token)
    }

    @Test
    fun `Quick Connect turned off asks for no code`() {
        jellyfin.quickConnectEnabled = false
        val provider = JellyfinQuickConnect(connection, pollMillis = 1L)
        val probed = runBlocking { JellyfinProbe(connection).probe(address()) }
        try {
            runBlocking { provider.start(probed, forTelevision = false) }
            fail("a code was asked for")
        } catch (_: LinkNotOfferedException) {
            // expected
        }
    }

    @Test
    fun `a code nobody approves expires with the window`() {
        val provider = JellyfinQuickConnect(connection, pollMillis = 1L, window = 20.milliseconds)
        val probed = runBlocking { JellyfinProbe(connection).probe(address()) }
        val link = runBlocking { provider.start(probed, forTelevision = false) }
        try {
            runBlocking { provider.await(probed, link) }
            fail("the wait did not end")
        } catch (_: LinkExpiredException) {
            // expected
        }
    }

    @Test
    fun `a kept session begins the signed-in state and drops the last account's index`() {
        val handoff = SessionHandoff(sessions, LibraryIndex())
        val begun = runBlocking { handoff.save("http://truenas.local:8096/".toHttpUrl(), info, signedIn) }
        assertEquals("http://truenas.local:8096", begun?.session?.baseUrl)
        assertEquals("token-1", begun?.session?.token)
        assertNotNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a session the store refuses begins nothing`() {
        val refusing =
            object : SessionStore by sessions {
                override suspend fun save(session: JellyfinSession) = false
            }
        val begun = runBlocking { SessionHandoff(refusing, LibraryIndex()).save("http://truenas.local:8096/".toHttpUrl(), info, signedIn) }
        assertNull(begun)
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `an error of the flow is worded as the screens have always worded it`() {
        assertEquals(SetupError.InvalidAddress, ConnectError.InvalidAddress.toSetupError())
        assertEquals(SetupError.NotJellyfin, ConnectError.NotThisServer.toSetupError())
        assertEquals(SetupError.QuickConnectExpired, ConnectError.LinkExpired.toSetupError())
        assertEquals(SetupError.QuickConnectOff, ConnectError.LinkNotOffered.toSetupError())
        assertEquals(SetupError.Unreachable, ConnectError.Failed(ServerFailure.Unreachable()).toSetupError())
        assertEquals(SetupError.LocalNetworkDenied, ConnectError.Failed(ServerFailure.LocalNetworkDenied).toSetupError())
        assertEquals(SetupError.WrongCredentials, ConnectError.Failed(ServerFailure.SessionRejected).toSetupError())
        assertEquals(SetupError.ClientCertificateNeeded, ConnectError.Failed(ServerFailure.ClientCertificateNeeded).toSetupError())
        assertEquals(SetupError.CleartextRefused, ConnectError.Failed(ServerFailure.CleartextBlocked).toSetupError())
        assertEquals(SetupError.Generic, ConnectError.Failed(ServerFailure.ServerError).toSetupError())
    }

    @Test
    fun `the address step shows what was typed, keeps the servers found, and the sign-in step keeps the accounts listed`() {
        val found =
            listOf(
                com.cooper.homelab.ephyra.server
                    .DiscoveredServer("id-1", "truenas", "http://truenas.local:8096"),
            )
        val step = UiState.EnterServer(address = "old", discovered = found, discovering = true)
        val address = ConnectState.Address("truenas.local", false, false, true, null, false, false)
        val shown = projectSetup(address, null, step) as UiState.EnterServer
        assertEquals("truenas.local", shown.address)
        assertTrue(shown.checking)
        assertEquals(found, shown.discovered)

        val form = SignInForm(SetupMode.Password).with(USERNAME_FIELD, "Claude").with(PASSWORD_FIELD, "right")
        val signIn = projectSetup(ConnectState.SignIn(server(), form, true, null), null, shown) as UiState.SignIn
        assertEquals("Claude", signIn.username)
        assertEquals("right", signIn.password)
        assertTrue(signIn.busy)
        assertEquals("http://truenas.local:8096", signIn.baseUrl)
    }

    @Test
    fun `a Quick Connect failure is shown to Quick Connect and a password one to the password form`() {
        val failed = ConnectError.Failed(ServerFailure.ServerError)
        val quick = SignInForm(SetupMode.QuickConnect)
        val onQuick = projectSetup(ConnectState.SignIn(server(), quick, false, failed), null, UiState.Loading) as UiState.SignIn
        assertEquals(SetupError.Generic, onQuick.quickConnectError)
        assertNull(onQuick.passwordError)
        val onPassword =
            projectSetup(
                ConnectState.SignIn(server(), SignInForm(SetupMode.Password), false, failed),
                null,
                UiState.Loading,
            ) as UiState.SignIn
        assertEquals(SetupError.Generic, onPassword.passwordError)
        assertNull(onPassword.quickConnectError)
    }

    @Test
    fun `a wizard that is open is left alone`() {
        val wizard = UiState.Startup("http://truenas.local:8096", info.copy(startupWizardCompleted = false))
        assertEquals(wizard, projectSetup(ConnectState.Loading, null, wizard))
    }

    @Test
    fun `every error the screens hold survives the trip to the flow's error and back`() {
        SetupError.entries.forEach { assertEquals(it, it.toConnectError().toSetupError()) }
    }

    @Test
    fun `the server step is the address step, asking for the local network where that is what failed`() {
        val failed = UiState.EnterServer(address = "truenas.local", error = SetupError.LocalNetworkDenied, checking = false)
        val address = failed.toConnectState()
        assertTrue(address.needsLocalNetwork)
        assertTrue(address.canTryDemo)
        assertEquals("truenas.local", address.address)
    }

    @Test
    fun `the sign-in step is the password form, busy while a code waits, and a server not set up is its notice`() {
        val step =
            UiState.SignIn(
                baseUrl = "http://truenas.local:8096",
                server = info,
                username = "sam",
                password = "x",
                quickConnectCode = "123456",
            )
        val state = step.toConnectState() as ConnectState.SignIn
        assertEquals(SetupMode.Password, state.form.mode)
        assertEquals("sam", state.form.value(USERNAME_FIELD))
        assertTrue(state.isConnecting)
        assertEquals("123456", step.linkState()?.code)
        assertEquals(listOf<Any>(SetupMode.Password, SetupMode.QuickConnect), state.server.modes)

        val notSetUp = step.copy(server = info.copy(startupWizardCompleted = false), quickConnectCode = null).toConnectState()
        assertTrue(notSetUp is ConnectState.NotSetUp)
    }

    @Test
    fun `an edit of the form is reported as the fields it changed`() {
        val before = SignInForm(SetupMode.Password, mapOf(USERNAME_FIELD to "sam", PASSWORD_FIELD to ""))
        assertEquals(mapOf(PASSWORD_FIELD to "secret"), before.changesTo(before.with(PASSWORD_FIELD, "secret")))
        assertEquals(emptyMap<String, String>(), before.changesTo(before))
    }
}
