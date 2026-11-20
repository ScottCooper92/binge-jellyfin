package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.JellyfinCollectionRepository
import com.cooper.homelab.ephyra.browse.JellyfinTitleRepository
import com.cooper.homelab.ephyra.browse.JellyfinUserDataRepository
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The movie page against a fake server: the title loads, the extra rows load after it, and a mark follows what the server says. */
class MovieViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client =
        JellyfinClient(
            OkHttpClient(),
            jellyfin.baseUrl.toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1.0"),
            Dispatchers.IO,
            logger = DiscardLogger,
        ).withToken("token-1")
    private val socket = MutableSharedFlow<List<UserDataChange>>(extraBufferCapacity = 8)

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private fun viewModel(version: JellyfinVersion = JellyfinVersion(12, 4, 0)): MovieViewModel {
        val profile = JellyfinServerProfile(version)
        return MovieViewModel(
            scope = scope,
            dispatcher = Dispatchers.Default,
            titles = JellyfinTitleRepository(client, "user-1", profile),
            userData = JellyfinUserDataRepository(client, "user-1", profile, socket),
            collections = JellyfinCollectionRepository(client, "user-1", profile),
            classify = { it.serverFailure() },
        )
    }

    private fun add(
        id: String,
        name: String,
        type: String = "Movie",
        collections: List<String> = emptyList(),
        played: Boolean = false,
    ) = jellyfin.browse.titles.add(FakeTitle(id, name, "lib-movies", type, year = 2000, played = played, collectionIds = collections))

    private suspend fun MovieViewModel.ready(until: (MovieUi) -> Boolean = { true }): MovieUi =
        withTimeout(TIMEOUT) { state.first { it is MovieUiState.Ready && until(it.movie) } }.let { (it as MovieUiState.Ready).movie }

    @Test
    fun `the title loads, then the similar titles and the collection arrive after it`() =
        runBlocking {
            add("m1", "Alien", collections = listOf("box"))
            add("m2", "Aliens")
            jellyfin.browse.titles.add(FakeTitle("box", "Alien Collection", "lib-box", "BoxSet"))
            jellyfin.browse.titles.add(FakeTitle("p1", "Alien", "box"))
            val model = viewModel().also { it.start("m1") }
            val movie = model.ready { it.similar.isNotEmpty() && it.collection != null }
            assertEquals("Alien", movie.title)
            assertEquals(listOf("m2"), movie.similar.map { it.id })
            assertEquals("Alien Collection", movie.collection!!.name)
            assertEquals(listOf("p1"), movie.collection!!.parts.map { it.id })
        }

    @Test
    fun `a film in two box sets shows the first one the server names`() =
        runBlocking {
            add("m1", "Alien", collections = listOf("boxA", "boxB"))
            jellyfin.browse.titles.add(FakeTitle("boxA", "Alien Collection", "lib-box", "BoxSet"))
            jellyfin.browse.titles.add(FakeTitle("boxB", "Space Horror", "lib-box", "BoxSet"))
            jellyfin.browse.titles.add(FakeTitle("pa", "Alien", "boxA"))
            jellyfin.browse.titles.add(FakeTitle("pb", "Event Horizon", "boxB"))
            val movie = viewModel().also { it.start("m1") }.ready { it.collection != null }
            assertEquals("boxA", movie.collection!!.id)
            assertEquals(listOf("pa"), movie.collection!!.parts.map { it.id })
        }

    @Test
    fun `a server that cannot name a collection has no row, and the page still shows`() =
        runBlocking {
            add("m1", "Alien")
            val model = viewModel(JellyfinVersion(10, 11, 6)).also { it.start("m1") }
            val movie = model.ready()
            assertNull(movie.collection)
            Thread.sleep(SETTLE_MILLIS)
            assertNull((model.state.value as MovieUiState.Ready).movie.collection)
        }

    @Test
    fun `a similar row that fails is left out and the page stays`() =
        runBlocking {
            add("m1", "Alien")
            jellyfin.status["/Items/m1/Similar"] = 500
            val model = viewModel().also { it.start("m1") }
            val movie = model.ready()
            Thread.sleep(SETTLE_MILLIS)
            assertTrue((model.state.value as MovieUiState.Ready).movie.similar.isEmpty())
            assertEquals("Alien", movie.title)
        }

    @Test
    fun `a title the server refuses is a failure, and a retry reads it again`() =
        runBlocking {
            add("m1", "Alien")
            jellyfin.status["/Items/m1"] = 500
            val model = viewModel().also { it.start("m1") }
            val failed = withTimeout(TIMEOUT) { model.state.first { it is MovieUiState.Failed } } as MovieUiState.Failed
            assertEquals(ServerFailure.ServerError, failed.failure)
            jellyfin.status.clear()
            model.retry()
            assertEquals("Alien", model.ready().title)
        }

    @Test
    fun `an id the server does not have is a failure`() =
        runBlocking {
            add("m1", "Alien")
            val model = viewModel().also { it.start("nobody") }
            assertTrue(withTimeout(TIMEOUT) { model.state.first { it is MovieUiState.Failed } } is MovieUiState.Failed)
        }

    @Test
    fun `watched and favourite follow the server's answer`() =
        runBlocking {
            add("m1", "Alien")
            val model = viewModel().also { it.start("m1") }
            model.ready()
            model.toggleWatched()
            assertTrue(model.ready { it.watched }.watched)
            model.toggleFavourite()
            assertTrue(model.ready { it.favourite }.favourite)
            model.toggleWatched()
            assertEquals(false, model.ready { !it.watched }.watched)
        }

    @Test
    fun `a change made in another app shows on the page`() =
        runBlocking {
            add("m1", "Alien")
            val model = viewModel().also { it.start("m1") }
            model.ready()
            Thread.sleep(SETTLE_MILLIS)
            socket.emit(listOf(UserDataChange("user-1", "m1", played = true, favourite = true)))
            val movie = model.ready { it.watched }
            assertTrue(movie.favourite)
        }

    @Test
    fun `a mark the server refuses is a message and changes nothing`() =
        runBlocking {
            add("m1", "Alien")
            val model = viewModel().also { it.start("m1") }
            model.ready()
            jellyfin.status["/UserPlayedItems/m1"] = 500
            val event = scope.async { withTimeout(TIMEOUT) { model.events.first() } }
            Thread.sleep(SETTLE_MILLIS)
            model.toggleWatched()
            assertEquals(TitleEvent.WriteFailed(ServerFailure.ServerError), event.await())
            assertEquals(false, (model.state.value as MovieUiState.Ready).movie.watched)
        }

    private companion object {
        const val TIMEOUT = 15_000L
        const val SETTLE_MILLIS = 500L
    }
}
