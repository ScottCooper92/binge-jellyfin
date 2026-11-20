package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.client.libraries.LibrariesUiState
import com.cooper.homelab.ephyra.client.libraries.LibraryTile
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** The Libraries tab's host: it reads the signed-in client, builds the tab's model on it, and hands the screen picture URLs with no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibrariesTabViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    /** The view model's scope is stopped, and finished, before the fake server closes and Main is reset (#493, #816). */
    @After
    fun close() = teardown.close(jellyfin)

    private fun viewModel() =
        teardown.made(LibrariesTabViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"))).also { it.start() }

    private fun LibrariesTabViewModel.settled(matches: (LibrariesUiState) -> Boolean = { it !is LibrariesUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    @Test
    fun `with a session it lists the libraries of the user's server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val ready = viewModel().settled() as LibrariesUiState.Ready
        assertTrue(ready.tiles.isNotEmpty())
        assertTrue(ready.tiles.all { it.kind == LibraryKind.Movies || it.kind == LibraryKind.TvShows })
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(LibrariesUiState.Error(ServerFailure.NotConnected), vm.settled())
        runBlocking { sessions.save(jellyfin.session()) }
        vm.refresh()
        assertTrue(vm.settled { it is LibrariesUiState.Ready } is LibrariesUiState.Ready)
    }

    @Test
    fun `a picture URL names the library and the tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.settled { it is LibrariesUiState.Ready }
        val url = vm.imageUrl(LibraryTile("lib-movies", "Movies", LibraryKind.Movies, "tag-1"), 300).orEmpty()
        assertTrue(url, url.contains("/Items/lib-movies/Images/Primary"))
        assertTrue(url, url.contains("tag=tag-1") && url.contains("maxWidth=300"))
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `a library with no picture, or no client read yet, has no picture URL`() {
        val vm = viewModel()
        assertNull(vm.imageUrl(LibraryTile("lib-movies", "Movies", LibraryKind.Movies, "tag-1"), 300))
        runBlocking { sessions.save(jellyfin.session()) }
        vm.refresh()
        vm.settled { it is LibrariesUiState.Ready }
        assertNull(vm.imageUrl(LibraryTile("lib-movies", "Movies", LibraryKind.Movies, imageTag = null), 300))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
