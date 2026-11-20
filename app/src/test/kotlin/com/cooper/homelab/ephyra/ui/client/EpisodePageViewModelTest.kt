package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.EpisodeUiState
import com.cooper.homelab.ephyra.client.title.EpisodesState
import com.cooper.homelab.ephyra.client.title.PlayKind
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

/** The page's host: it reads the signed-in client, builds the episode page on it, and hands the screen picture URLs that carry no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EpisodePageViewModelTest {
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

    private fun viewModel(id: String = "e1") =
        teardown
            .made(EpisodePageViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"), socket))
            .also { it.start(id) }

    private fun EpisodePageViewModel.settled(matches: (EpisodeUiState) -> Boolean = { it !is EpisodeUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    private fun seed() {
        jellyfin.browse.titles.add(FakeTitle("series", "Alien Nights", "lib-shows", "Series", year = 1979))
        jellyfin.browse.titles.add(
            FakeTitle("s1", "Season 1", "lib-shows", "Season", seriesId = "series", season = 1, extra = "\"IndexNumber\":1"),
        )
        listOf("e1" to "Pilot", "e2" to "Second").forEachIndexed { index, (id, name) ->
            jellyfin.browse.titles.add(
                FakeTitle(
                    id,
                    name,
                    "lib-shows",
                    "Episode",
                    seriesId = "series",
                    seriesName = "Alien Nights",
                    season = 1,
                    episode = index + 1,
                    seasonId = "s1",
                ),
            )
        }
    }

    @Test
    fun `with a session it shows the episode from the user's server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val ready = viewModel().settled { it is EpisodeUiState.Ready }
        assertEquals("Pilot", (ready as EpisodeUiState.Ready).episode.title)
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(EpisodeUiState.Failed(ServerFailure.NotConnected), vm.settled())
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        vm.retry()
        assertTrue(vm.settled { it is EpisodeUiState.Ready } is EpisodeUiState.Ready)
    }

    @Test
    fun `a mark made on the page reaches the server and shows on the page`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val vm = viewModel()
        vm.settled { it is EpisodeUiState.Ready }
        vm.toggleFavourite()
        assertTrue((vm.settled { it is EpisodeUiState.Ready && it.episode.favourite } as EpisodeUiState.Ready).episode.favourite)
    }

    @Test
    fun `the season's episodes load after the episode, and the page can read them again`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val vm = viewModel()
        val ready = vm.settled { it is EpisodeUiState.Ready && it.episode.season is EpisodesState.Ready }
        assertEquals(listOf("e1", "e2"), ((ready as EpisodeUiState.Ready).episode.season as EpisodesState.Ready).episodes.map { it.id })
        vm.retrySeason()
        assertTrue(vm.settled { it is EpisodeUiState.Ready && it.episode.season is EpisodesState.Ready } is EpisodeUiState.Ready)
    }

    @Test
    fun `an episode nobody has watched shows as not watched and not a favourite, on a cold start`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val ready = viewModel("e2").settled { it is EpisodeUiState.Ready }
        val episode = (ready as EpisodeUiState.Ready).episode
        assertFalse("watched", episode.watched)
        assertFalse("favourite", episode.favourite)
        assertEquals(PlayKind.Play, episode.play.kind)
    }

    @Test
    fun `a picture URL names the item and the tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        seed()
        val vm = viewModel()
        vm.settled { it is EpisodeUiState.Ready }
        val url = vm.imageUrl(TitleImage("e1", ImageKind.Primary, "p-e1"), 1080).orEmpty()
        assertTrue(url, url.contains("/Items/e1/Images/Primary"))
        assertTrue(url, url.contains("tag=p-e1") && url.contains("maxWidth=1080"))
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `there is no picture URL before the client is read`() {
        assertNull(viewModel().imageUrl(TitleImage("e1", ImageKind.Primary, "p"), 300))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
