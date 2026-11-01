package com.cooper.binge.companion.jellyfin.ui

import com.cooper.binge.companion.jellyfin.FakeItem
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.DiscoveredServer
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** Setup and settings against a fake server. The server calls run on a real IO thread, so each step waits for its state. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class JellyfinViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions: SessionStore = testSessionStore()
    private val index = LibraryIndex()
    private val connection = Connection(OkHttpClient(), sessions, "Binge Jellyfin", "Test", "1.0", Dispatchers.IO)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        jellyfin.items += FakeItem("m", "Companion", "Movie", tmdbId = 1084199)
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        jellyfin.close()
    }

    private var found: List<DiscoveredServer> = emptyList()

    private fun viewModel() = JellyfinViewModel(sessions, connection, index, discover = { found }, quickConnectPollMillis = 10L)

    private inline fun <reified T : UiState> JellyfinViewModel.await(crossinline matches: (T) -> Boolean = { true }): T =
        runBlocking { withTimeout(10_000) { state.first { it is T && matches(it) } as T } }

    private fun JellyfinViewModel.toSignIn(): UiState.SignIn {
        await<UiState.EnterServer>()
        onAddress(jellyfin.baseUrl)
        connect()
        return await()
    }

    @Test
    fun `an address that is not one is refused before any request`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.onAddress("http://")
        vm.connect()
        assertEquals(SetupError.InvalidAddress, vm.await<UiState.EnterServer> { it.error != null }.error)
        assertEquals(0, jellyfin.requests.size)
    }

    @Test
    fun `a server that answers is named, and an http one is marked as not encrypted`() {
        val signIn = viewModel().toSignIn()
        assertEquals("truenas", signIn.server.serverName)
        assertTrue(signIn.cleartext)
    }

    @Test
    fun `an address that is not a Jellyfin server, or not reachable, says which`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        jellyfin.status["/System/Info/Public"] = 404
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        assertEquals(SetupError.NotJellyfin, vm.await<UiState.EnterServer> { it.error != null }.error)

        jellyfin.close()
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        assertEquals(SetupError.Unreachable, vm.await<UiState.EnterServer> { it.error != null }.error)
    }

    @Test
    fun `a wrong password says so, a right one signs in and counts the library`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.onUsername("Claude")
        vm.onPassword("wrong")
        vm.signInWithPassword()
        assertEquals(SetupError.WrongCredentials, vm.await<UiState.SignIn> { it.passwordError != null }.passwordError)

        vm.onPassword("right")
        vm.signInWithPassword()
        val signedIn = vm.await<UiState.SignedIn> { it.titles != null }
        assertEquals("Claude", signedIn.session.userName)
        assertEquals(1, signedIn.titles)
        assertEquals("token-1", runBlocking { sessions.current() }?.token)
    }

    @Test
    fun `Quick Connect shows the code and signs in once approved`() {
        val vm = viewModel()
        vm.toSignIn()
        vm.startQuickConnect()
        assertEquals("123456", vm.await<UiState.SignIn> { it.quickConnectCode != null }.quickConnectCode)
        jellyfin.quickConnectApproved = true
        vm.await<UiState.SignedIn>()
        assertEquals("user-1", runBlocking { sessions.current() }?.userId)
    }

    @Test
    fun `Quick Connect off on the server says so, and a cancelled code goes away`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        assertEquals(SetupError.QuickConnectOff, vm.await<UiState.SignIn> { it.quickConnectError != null }.quickConnectError)

        jellyfin.quickConnectEnabled = true
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectCode != null }
        vm.cancelQuickConnect()
        assertNull(vm.await<UiState.SignIn> { it.quickConnectCode == null }.quickConnectCode)
        vm.changeServer()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
    }

    @Test
    fun `signing out ends the session on the server and here, and keeps the address`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        vm.confirmSignOut(true)
        assertTrue(vm.await<UiState.SignedIn> { it.confirmingSignOut }.confirmingSignOut)
        vm.confirmSignOut(false)
        vm.await<UiState.SignedIn> { !it.confirmingSignOut }
        vm.signOut()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertEquals(1, jellyfin.hits("/Sessions/Logout"))
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a refresh that cannot reach the server says so`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.status["/Items"] = 500
        vm.refresh()
        assertTrue(vm.await<UiState.SignedIn> { it.refreshFailed }.refreshFailed)
    }

    @Test
    fun `a token the server refuses sends the user back to sign in`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.status["/Items"] = 401
        vm.refresh()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a token a console page found refused sends the user to sign in again`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        connection.reportRefused()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.EnterServer>().address)
        assertNull(runBlocking { sessions.current() })
    }

    @Test
    fun `a refresh answered with something that is not JSON fails without crashing`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.await<UiState.SignedIn> { it.titles != null }
        jellyfin.notJson += "/Items"
        vm.refresh()
        assertTrue(vm.await<UiState.SignedIn> { it.refreshFailed }.refreshFailed)
    }

    @Test
    fun `a server address that answers a web page is not Jellyfin, and a sign-in that does is a generic failure`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        jellyfin.notJson += "/System/Info/Public"
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        assertEquals(SetupError.NotJellyfin, vm.await<UiState.EnterServer> { it.error != null }.error)

        jellyfin.notJson.clear()
        vm.connect()
        vm.await<UiState.SignIn>()
        jellyfin.notJson += "/Users/AuthenticateByName"
        vm.onUsername("Claude")
        vm.onPassword("right")
        vm.signInWithPassword()
        assertEquals(SetupError.Generic, vm.await<UiState.SignIn> { it.passwordError != null }.passwordError)
    }

    @Test
    fun `each sign-in method keeps its own failure, so switching never shows the other's`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.status["/Users/AuthenticateByName"] = 500
        vm.onUsername("Claude")
        vm.onPassword("right")
        vm.signInWithPassword()
        vm.await<UiState.SignIn> { it.passwordError == SetupError.Generic }
        // Switching to Quick Connect asks for a code rather than showing the password's failure.
        vm.startQuickConnect()
        val code = vm.await<UiState.SignIn> { it.quickConnectCode != null }
        assertNull(code.quickConnectError)

        // A Quick Connect failure stays with Quick Connect, and leaving it takes the failure along.
        vm.cancelQuickConnect()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectError == SetupError.QuickConnectOff }
        vm.cancelQuickConnect()
        val back = vm.await<UiState.SignIn> { it.quickConnectError == null }
        assertEquals(false, back.gettingCode)
        // Typing clears the password's own failure.
        vm.onPassword("again")
        assertNull((vm.state.value as UiState.SignIn).passwordError)
    }

    @Test
    fun `retrying Quick Connect drops the last failure at once and shows it's getting a code`() {
        val vm = viewModel()
        vm.toSignIn()
        jellyfin.quickConnectEnabled = false
        vm.startQuickConnect()
        vm.await<UiState.SignIn> { it.quickConnectError != null }
        jellyfin.slow += "/QuickConnect/Enabled"
        vm.startQuickConnect()
        val retrying = vm.state.value as UiState.SignIn
        assertNull(retrying.quickConnectError)
        assertTrue(retrying.gettingCode)
        vm.cancelQuickConnect()
    }

    @Test
    fun `an address that redirects signs in at, and saves, the server the redirect leads to`() {
        // A proxy in front of the server that answers every request with a 301 to the server itself,
        // as an http-to-https redirect does. OkHttp would turn the sign-in POST into a GET through it.
        val proxy = mockwebserver3.MockWebServer()
        proxy.dispatcher =
            object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse =
                    mockwebserver3.MockResponse
                        .Builder()
                        .code(301)
                        .addHeader("Location", jellyfin.baseUrl + request.url.encodedPath)
                        .build()
            }
        proxy.start()
        try {
            val vm = viewModel()
            vm.await<UiState.EnterServer>()
            vm.onAddress(proxy.url("/").toString())
            vm.connect()
            assertEquals(jellyfin.baseUrl, vm.await<UiState.SignIn>().baseUrl)

            vm.onUsername("Claude")
            vm.onPassword("right")
            vm.signInWithPassword()
            vm.await<UiState.SignedIn>()
            assertEquals(jellyfin.baseUrl, runBlocking { sessions.current() }?.baseUrl)
            // Only the first request went through the redirect.
            assertEquals(1, proxy.requestCount)
        } finally {
            proxy.close()
        }
    }

    @Test
    fun `servers found on the network are listed, and choosing one connects to it`() {
        found = listOf(DiscoveredServer(id = "server-1", name = "truenas", address = jellyfin.baseUrl))
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.discoverServers()
        assertEquals(found, vm.await<UiState.EnterServer> { it.discovered.isNotEmpty() }.discovered)
        vm.onAddress(found.single().address)
        vm.connect()
        assertEquals(jellyfin.baseUrl, vm.await<UiState.SignIn>().baseUrl)
    }

    @Test
    fun `nothing found leaves the typed address as the way in, with no error`() {
        val vm = viewModel()
        vm.await<UiState.EnterServer>()
        vm.discoverServers()
        val state = vm.await<UiState.EnterServer> { !it.discovering }
        assertTrue(state.discovered.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `a failed check that outlasts the search keeps what the search found`() {
        val gate = CompletableDeferred<Unit>()
        val server = listOf(DiscoveredServer(id = "server-1", name = "truenas", address = jellyfin.baseUrl))
        // Accepts the connection and never answers, so the check fails by timeout after the search is done.
        ServerSocket(0).use { silent ->
            val slow = OkHttpClient.Builder().callTimeout(500, TimeUnit.MILLISECONDS).build()
            val vm =
                JellyfinViewModel(
                    sessions,
                    Connection(slow, sessions, "Binge Jellyfin", "Test", "1.0", Dispatchers.IO),
                    index,
                    discover = {
                        gate.await()
                        server
                    },
                    quickConnectPollMillis = 10L,
                )
            vm.await<UiState.EnterServer>()
            vm.discoverServers()
            vm.onAddress("http://127.0.0.1:${silent.localPort}")
            vm.connect()
            vm.await<UiState.EnterServer> { it.checking }
            gate.complete(Unit)
            val state = vm.await<UiState.EnterServer> { it.error == SetupError.Unreachable }
            assertTrue(!state.discovering)
            assertEquals(server, state.discovered)
        }
    }

    @Test
    fun `an administrator is marked as one once the library is read`() {
        jellyfin.administrator = true
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        assertTrue(vm.await<UiState.SignedIn> { it.isAdministrator }.isAdministrator)
    }
}
