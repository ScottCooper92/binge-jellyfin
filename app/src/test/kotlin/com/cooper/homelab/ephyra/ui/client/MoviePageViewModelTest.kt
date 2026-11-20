package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.MovieUiState
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

/** The page's host: it reads the signed-in client, builds the movie page on it, and hands the screen picture URLs that carry no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MoviePageViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val socket = MutableSharedFlow<List<UserDataChange>>(extraBufferCapacity = 4)
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    /** The view model's scope is stopped, and finished, before the fake server closes and Main is reset (#493, #816). */
    @After
    fun close() = teardown.close(jellyfin)

    private fun viewModel(id: String = "m1") =
        teardown
            .made(MoviePageViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"), socket))
            .also { it.start(id) }

    private fun MoviePageViewModel.settled(matches: (MovieUiState) -> Boolean = { it !is MovieUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    @Test
    fun `with a session it shows the film from the user's server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", year = 1979))
        val ready = viewModel().settled { it is MovieUiState.Ready }
        assertEquals("Alien", (ready as MovieUiState.Ready).movie.title)
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(MovieUiState.Failed(ServerFailure.NotConnected), vm.settled())
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", year = 1979))
        vm.retry()
        assertTrue(vm.settled { it is MovieUiState.Ready } is MovieUiState.Ready)
    }

    @Test
    fun `a mark made on the page reaches the server and shows on the page`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", year = 1979))
        val vm = viewModel()
        vm.settled { it is MovieUiState.Ready }
        vm.toggleFavourite()
        assertTrue((vm.settled { it is MovieUiState.Ready && it.movie.favourite } as MovieUiState.Ready).movie.favourite)
    }

    @Test
    fun `a picture URL names the item and the tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("m1", "Alien", "lib-movies", year = 1979))
        val vm = viewModel()
        vm.settled { it is MovieUiState.Ready }
        val url = vm.imageUrl(TitleImage("m1", ImageKind.Backdrop, "b-m1"), 1080).orEmpty()
        assertTrue(url, url.contains("/Items/m1/Images/Backdrop"))
        assertTrue(url, url.contains("tag=b-m1") && url.contains("maxWidth=1080"))
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `there is no picture URL before the client is read`() {
        assertNull(viewModel().imageUrl(TitleImage("m1", ImageKind.Primary, "p"), 300))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
