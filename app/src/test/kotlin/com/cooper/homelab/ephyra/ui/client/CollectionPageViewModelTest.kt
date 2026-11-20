package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.CollectionUiState
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

/** The page's host: it reads the signed-in client, builds the collection page on it, and hands the screen picture URLs that carry no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CollectionPageViewModelTest {
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

    private fun viewModel(id: String = "box") =
        teardown
            .made(CollectionPageViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"), socket))
            .also { it.start(id) }

    private fun CollectionPageViewModel.settled(matches: (CollectionUiState) -> Boolean = { it !is CollectionUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    private fun seed() {
        jellyfin.browse.titles.add(FakeTitle("box", "Alien Collection", "lib-box", "BoxSet", extra = """"Overview":"All of them.""""))
        jellyfin.browse.titles.add(FakeTitle("a2", "Aliens", "box", year = 1986))
        jellyfin.browse.titles.add(FakeTitle("a1", "Alien", "box", year = 1979, played = true))
    }

    @Test
    fun `with a session it shows the collection and its parts from the user's server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val ready = viewModel().settled { it is CollectionUiState.Ready }
        assertEquals(listOf("Alien", "Aliens"), (ready as CollectionUiState.Ready).collection.parts.map { it.card.title })
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(CollectionUiState.Failed(ServerFailure.NotConnected), vm.settled())
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        vm.retry()
        assertTrue(vm.settled { it is CollectionUiState.Ready } is CollectionUiState.Ready)
    }

    @Test
    fun `a part watched elsewhere shows watched after the socket says so`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val vm = viewModel()
        vm.settled { it is CollectionUiState.Ready && it.collection.parts.size == 2 }
        socket.tryEmit(listOf(UserDataChange("user-1", "a2", played = true)))
        val marked =
            vm.settled { state ->
                state is CollectionUiState.Ready &&
                    state.collection.parts
                        .single { it.card.id == "a2" }
                        .card.played
            }
        assertTrue(marked is CollectionUiState.Ready)
    }

    @Test
    fun `a picture URL names the item and the tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val vm = viewModel()
        vm.settled { it is CollectionUiState.Ready }
        val url = vm.imageUrl(TitleImage("box", ImageKind.Backdrop, "b-box"), 1080).orEmpty()
        assertTrue(url, url.contains("/Items/box/Images/Backdrop"))
        assertTrue(url, url.contains("tag=b-box") && url.contains("maxWidth=1080"))
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `there is no picture URL before the client is read`() {
        assertNull(viewModel().imageUrl(TitleImage("box", ImageKind.Primary, "p"), 300))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
