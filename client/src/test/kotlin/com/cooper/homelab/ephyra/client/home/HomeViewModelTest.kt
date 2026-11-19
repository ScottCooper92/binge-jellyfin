package com.cooper.homelab.ephyra.client.home

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.JellyfinLatestRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.browse.JellyfinNextUpRepository
import com.cooper.homelab.ephyra.browse.JellyfinResumeRepository
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's rows against a fake server: each loads on its own, a failed or empty one is left out, and Home fails only when all did. */
class HomeViewModelTest {
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
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 6))

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private fun viewModel() =
        HomeViewModel(
            scope = scope,
            dispatcher = Dispatchers.Default,
            libraries = JellyfinLibraryRepository(client, "user-1", profile),
            resume = JellyfinResumeRepository(client, "user-1", profile),
            nextUp = JellyfinNextUpRepository(client, "user-1"),
            latest = JellyfinLatestRepository(client, "user-1", profile),
            classify = { it.serverFailure() },
        ).also { it.start() }

    private fun movie(
        id: String,
        name: String,
        position: Long = 0L,
        added: Int = 0,
    ) = jellyfin.browse.titles.add(FakeTitle(id, name, "lib-movies", positionTicks = position, added = added, lastPlayed = added))

    private fun HomeViewModel.settled(matches: (HomeUiState) -> Boolean = { it !is HomeUiState.Loading }): HomeUiState =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    private fun HomeUiState.keys() = (this as HomeUiState.Ready).rows.map { it.key }

    @Test
    fun `the rows that have titles show, in order, and the ones with none are left out`() {
        movie("a", "Alien", position = 5, added = 1)
        movie("b", "Brazil", added = 2)
        val ready = viewModel().settled { it is HomeUiState.Ready && it.pending == 0 }
        assertEquals(listOf(HomeRowKey.ContinueWatching, HomeRowKey.Latest("lib-movies", "Movies")), ready.keys())
        assertEquals("a", (ready as HomeUiState.Ready).hero?.card?.id)
    }

    @Test
    fun `a row that fails is left out and the others show`() {
        movie("a", "Alien", position = 5, added = 1)
        jellyfin.status["/UserItems/Resume"] = 500
        val ready = viewModel().settled { it is HomeUiState.Ready && it.pending == 0 }
        assertEquals(listOf<HomeRowKey>(HomeRowKey.Latest("lib-movies", "Movies")), ready.keys())
    }

    @Test
    fun `every row failing is one error, classified`() {
        movie("a", "Alien", position = 5)
        listOf("/UserItems/Resume", "/Shows/NextUp", "/Items/Latest").forEach { jellyfin.status[it] = 500 }
        val state = viewModel().settled()
        assertEquals(HomeUiState.Error(ServerFailure.ServerError), state)
    }

    @Test
    fun `libraries that cannot be read are an error, and a refresh tries again`() {
        movie("a", "Alien", added = 1)
        jellyfin.status["/UserViews"] = 503
        val vm = viewModel()
        assertTrue(vm.settled() is HomeUiState.Error)
        jellyfin.status.clear()
        vm.refresh()
        assertTrue(vm.settled { it is HomeUiState.Ready }.keys().isNotEmpty())
    }

    @Test
    fun `a server with no titles is empty, not an error`() {
        assertEquals(HomeUiState.Empty, viewModel().settled())
    }

    @Test
    fun `a refresh shows a title added since`() {
        movie("a", "Alien", added = 1)
        val vm = viewModel()
        vm.settled { it is HomeUiState.Ready && it.pending == 0 }
        movie("b", "Brazil", added = 2)
        vm.refresh()
        val names =
            runBlocking {
                withTimeout(TIMEOUT_MILLIS) {
                    vm.state.first { s ->
                        s is HomeUiState.Ready &&
                            s.rows
                                .single()
                                .cards.size == 2
                    }
                }
            }
        assertEquals(
            listOf("Brazil", "Alien"),
            (names as HomeUiState.Ready)
                .rows
                .single()
                .cards
                .map { it.title },
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
