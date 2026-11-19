package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.server.connection.CallResult
import com.cooper.homelab.core.server.connection.ConnectionHealth
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.home.HomeImage
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.SAVED_SERVER
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The tab's host: it reads the signed-in client, builds Home on it, and hands the screen picture URLs that carry no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeTabViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun close() {
        connection.close()
        jellyfin.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeTabViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6")).also { it.start() }

    private fun HomeTabViewModel.settled(matches: (HomeUiState) -> Boolean = { it !is HomeUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    @Test
    fun `with a session it shows the rows of the user's server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", added = 1))
        val ready = viewModel().settled { it is HomeUiState.Ready && it.pending == 0 }
        assertEquals(1, (ready as HomeUiState.Ready).rows.size)
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(HomeUiState.Error(ServerFailure.NotConnected), vm.settled())
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", added = 1))
        vm.refresh()
        assertTrue(vm.settled { it is HomeUiState.Ready } is HomeUiState.Ready)
    }

    @Test
    fun `a picture URL names the item and the tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", added = 1))
        val vm = viewModel()
        vm.settled { it is HomeUiState.Ready }
        val url = vm.imageUrl(HomeImage("m1", ImageKind.Primary, "p-m1"), 300).orEmpty()
        assertTrue(url, url.contains("/Items/m1/Images/Primary"))
        assertTrue(url, url.contains("tag=p-m1") && url.contains("maxWidth=300"))
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `there is no picture URL before the client is read`() {
        assertNull(viewModel().imageUrl(HomeImage("m1", ImageKind.Primary, "p"), 300))
    }

    @Test
    fun `a server the monitor reads as unreachable shows no connection while Home is still loading`() {
        val vm = HomeTabViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"))
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        assertEquals(HomeUiState.Error(ServerFailure.Unreachable()), vm.settled())
    }

    @Test
    fun `an answer from the server takes no connection away again`() {
        val vm = HomeTabViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"))
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        connection.monitor.report(SAVED_SERVER, CallResult.TransportFailure())
        vm.settled { it is HomeUiState.Error }
        connection.monitor.report(SAVED_SERVER, CallResult.Answered(200))
        assertEquals(HomeUiState.Loading, vm.settled { it is HomeUiState.Loading })
    }

    @Test
    fun `rows that have landed stay when the monitor reads unreachable`() {
        val loaded = HomeUiState.Ready(hero = null, rows = emptyList())
        assertEquals(loaded, homeShown(loaded, ConnectionHealth.Unreachable))
        assertEquals(HomeUiState.Loading, homeShown(HomeUiState.Loading, ConnectionHealth.Healthy))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
