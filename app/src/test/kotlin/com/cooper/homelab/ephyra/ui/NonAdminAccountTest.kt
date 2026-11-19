package com.cooper.homelab.ephyra.ui

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.cultures
import com.cooper.homelab.ephyra.server.groupingOptions
import com.cooper.homelab.ephyra.server.nowPlaying
import com.cooper.homelab.ephyra.server.serverInfo
import com.cooper.homelab.ephyra.server.userConfiguration
import com.cooper.homelab.ephyra.server.userViews
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.ui.console.ConsolePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An account that is not an administrator, against a fake server that refuses it the routes only an administrator may call (as the
 * real one does). Nothing it opens may reach such a route, so none may answer 403 into an error state (epic #759).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NonAdminAccountTest {
    private val jellyfin = FakeJellyfin().also { it.enforcesAdministrator = true }
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        // A plain user: preference access is on, which is the server's default.
        jellyfin.policy = ",\"EnableUserPreferenceAccess\":true"
    }

    @After
    fun close() {
        teardown.close(jellyfin)
        Dispatchers.resetMain()
    }

    private fun viewModel(): JellyfinViewModel =
        teardown.made(JellyfinViewModel(sessions, connection, LibraryIndex(), discover = { emptyList() }))

    private fun JellyfinViewModel.signedIn(matches: (UiState.SignedIn) -> Boolean): UiState.SignedIn =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is UiState.SignedIn && matches(it) } as UiState.SignedIn } }

    /** The home once the account is read and the card has every figure a user may read. */
    private fun JellyfinViewModel.home() = signedIn { it.account.isRead && it.card.playing != null && it.card.movies != null }

    @Test
    fun `a saved session of a plain user reaches the home with no admin read and no error`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val home = viewModel().home()
        assertTrue(home.card.connected)
        assertEquals(ConnectionHealth.Healthy, home.health)
        assertTrue(!home.isAdministrator)
        assertEquals(emptyList<String>(), jellyfin.forbidden.toList())
    }

    @Test
    fun `signing in with a password as a plain user reaches the home with no admin read and no error`() {
        val vm = viewModel()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is UiState.EnterServer } } }
        vm.onAddress(jellyfin.baseUrl)
        vm.connect()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is UiState.SignIn } } }
        vm.onUsername("Kid")
        vm.onPassword("")
        vm.signInWithPassword()
        val home = vm.home()
        assertEquals(ConnectionHealth.Healthy, home.health)
        assertEquals(emptyList<String>(), jellyfin.forbidden.toList())
    }

    @Test
    fun `a plain user is offered only the pages that are theirs`() {
        val account =
            JellyfinAccount("kid", Json.parseToJsonElement("""{"IsAdministrator":false,"EnableUserPreferenceAccess":true}""").jsonObject)
        val profile = JellyfinServerProfile.from("10.11.6", quickConnect = true)
        val offered = ConsolePage.entries.filter { it.available(profile, account) }.toSet()
        assertEquals(
            setOf(
                ConsolePage.ApproveCode,
                ConsolePage.Playback,
                ConsolePage.HomeLayout,
                ConsolePage.ProfilePicture,
                ConsolePage.Password,
                ConsolePage.NowPlaying,
                ConsolePage.Licences,
                ConsolePage.Diagnostics,
                ConsolePage.ThisApp,
                ConsolePage.Search,
            ),
            offered,
        )
        assertTrue("an administrator's page is offered", offered.none { it.isAdmin })
    }

    @Test
    fun `a plain user whose preference access is off is offered none of the preference pages`() {
        val account =
            JellyfinAccount("kid", Json.parseToJsonElement("""{"IsAdministrator":false,"EnableUserPreferenceAccess":false}""").jsonObject)
        val profile = JellyfinServerProfile.from("10.11.6", quickConnect = true)
        val offered = ConsolePage.entries.filter { it.available(profile, account) }.toSet()
        assertTrue(offered.none { it in setOf(ConsolePage.Playback, ConsolePage.HomeLayout, ConsolePage.ProfilePicture) })
    }

    @Test
    fun `the pages a plain user may open read only what the server lets them`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val profile = JellyfinServerProfile.from("10.11.6", quickConnect = true)
        runBlocking {
            val (session, client) = connection.signedIn() ?: error("no session")
            // Playback, the home layout and the playing list: each is a user's own read.
            client.userConfiguration()
            client.cultures()
            client.userViews(session.userId, profile)
            client.groupingOptions(session.userId, profile)
            client.nowPlaying()
        }
        assertEquals(emptyList<String>(), jellyfin.forbidden.toList())
    }

    @Test
    fun `the fake refuses a plain user an administrator's read, so the checks above can fail`() {
        runBlocking { sessions.save(jellyfin.session()) }
        runBlocking {
            val (_, client) = connection.signedIn() ?: error("no session")
            assertTrue(
                com.binge.companion.sdk
                    .attempt { client.serverInfo() }
                    .isFailure,
            )
        }
        assertEquals(listOf("GET /System/Info"), jellyfin.forbidden.toList())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
