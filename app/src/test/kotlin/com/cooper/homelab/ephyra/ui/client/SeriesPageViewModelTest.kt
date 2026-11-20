package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.EpisodesState
import com.cooper.homelab.ephyra.client.title.SeriesUiState
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

/** The page's host: it reads the signed-in client, builds the series page on it, and hands the screen picture URLs that carry no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SeriesPageViewModelTest {
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

    private fun viewModel(id: String = "series") =
        teardown
            .made(SeriesPageViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"), socket))
            .also { it.start(id) }

    private fun SeriesPageViewModel.settled(matches: (SeriesUiState) -> Boolean = { it !is SeriesUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    @Test
    fun `with a session it shows the series from the user's server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("series", "Alien Nights", "lib-shows", "Series", year = 1979))
        val ready = viewModel().settled { it is SeriesUiState.Ready }
        assertEquals("Alien Nights", (ready as SeriesUiState.Ready).series.title)
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(SeriesUiState.Failed(ServerFailure.NotConnected), vm.settled())
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("series", "Alien Nights", "lib-shows", "Series", year = 1979))
        vm.retry()
        assertTrue(vm.settled { it is SeriesUiState.Ready } is SeriesUiState.Ready)
    }

    @Test
    fun `a mark made on the page reaches the server and shows on the page`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("series", "Alien Nights", "lib-shows", "Series", year = 1979))
        val vm = viewModel()
        vm.settled { it is SeriesUiState.Ready }
        vm.toggleFavourite()
        assertTrue((vm.settled { it is SeriesUiState.Ready && it.series.favourite } as SeriesUiState.Ready).series.favourite)
    }

    @Test
    fun `a season chosen on the page shows its episodes, and an episode mark made on it reaches the server`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("series", "Alien Nights", "lib-shows", "Series", year = 1979))
        listOf("s1" to 1, "s2" to 2).forEach { (id, number) ->
            jellyfin.browse.titles.add(
                FakeTitle(
                    id,
                    "Season $number",
                    "lib-shows",
                    "Season",
                    seriesId = "series",
                    season = number,
                    extra = "\"IndexNumber\":$number",
                ),
            )
        }
        jellyfin.browse.titles.add(
            FakeTitle(
                "e1",
                "Pilot",
                "lib-shows",
                "Episode",
                seriesId = "series",
                seriesName = "Alien Nights",
                season = 1,
                episode = 1,
                seasonId = "s1",
            ),
        )
        jellyfin.browse.titles.add(
            FakeTitle(
                "e2",
                "Second",
                "lib-shows",
                "Episode",
                seriesId = "series",
                seriesName = "Alien Nights",
                season = 2,
                episode = 1,
                seasonId = "s2",
            ),
        )
        val vm = viewModel()
        vm.settled { it is SeriesUiState.Ready && it.series.episodes is EpisodesState.Ready }
        vm.selectSeason("s2")
        val second =
            vm.settled {
                it is SeriesUiState.Ready &&
                    (it.series.episodes as? EpisodesState.Ready)?.episodes?.map { e -> e.id } == listOf("e2")
            }
        assertEquals("s2", (second as SeriesUiState.Ready).series.selectedSeasonId)
        vm.toggleEpisodeWatched("e2")
        val marked =
            vm.settled { state ->
                state is SeriesUiState.Ready &&
                    (state.series.episodes as? EpisodesState.Ready)?.episodes?.single()?.watched == true
            }
        assertTrue(marked is SeriesUiState.Ready)
    }

    @Test
    fun `a picture URL names the item and the tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.browse.titles.add(FakeTitle("series", "Alien Nights", "lib-shows", "Series", year = 1979))
        val vm = viewModel()
        vm.settled { it is SeriesUiState.Ready }
        val url = vm.imageUrl(TitleImage("series", ImageKind.Backdrop, "b-series"), 1080).orEmpty()
        assertTrue(url, url.contains("/Items/series/Images/Backdrop"))
        assertTrue(url, url.contains("tag=b-series") && url.contains("maxWidth=1080"))
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `there is no picture URL before the client is read`() {
        assertNull(viewModel().imageUrl(TitleImage("series", ImageKind.Primary, "p"), 300))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
