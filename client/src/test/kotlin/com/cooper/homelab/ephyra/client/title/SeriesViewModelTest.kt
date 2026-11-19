package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
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
import org.junit.Assert.assertTrue
import org.junit.Test

/** The series page against a fake server: the series, its seasons and the shown season's episodes load, and marks follow the server. */
class SeriesViewModelTest {
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
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 6))

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private fun viewModel() =
        SeriesViewModel(
            scope = scope,
            dispatcher = Dispatchers.Default,
            titles = JellyfinTitleRepository(client, "user-1", profile),
            userData = JellyfinUserDataRepository(client, "user-1", profile, socket),
            classify = { it.serverFailure() },
        )

    private fun seed() {
        jellyfin.browse.titles.add(FakeTitle("series", "Show", "lib-shows", "Series", year = 2020))
        jellyfin.browse.titles.add(
            FakeTitle("sp", "Specials", "lib-shows", "Season", seriesId = "series", season = 0, extra = "\"IndexNumber\":0"),
        )
        jellyfin.browse.titles.add(
            FakeTitle("s1", "Season 1", "lib-shows", "Season", seriesId = "series", season = 1, extra = "\"IndexNumber\":1"),
        )
        jellyfin.browse.titles.add(
            FakeTitle("s2", "Season 2", "lib-shows", "Season", seriesId = "series", season = 2, extra = "\"IndexNumber\":2"),
        )
        listOf("e1" to "s1", "e2" to "s1", "e3" to "s2").forEachIndexed { index, (id, season) ->
            jellyfin.browse.titles.add(
                FakeTitle(
                    id,
                    "Episode ${index + 1}",
                    "lib-shows",
                    "Episode",
                    seriesId = "series",
                    seriesName = "Show",
                    season = season.removePrefix("s").toInt(),
                    episode = index + 1,
                    seasonId = season,
                ),
            )
        }
    }

    private suspend fun SeriesViewModel.ready(until: (SeriesUi) -> Boolean = { true }): SeriesUi =
        (withTimeout(TIMEOUT) { state.first { it is SeriesUiState.Ready && until(it.series) } } as SeriesUiState.Ready).series

    @Test
    fun `the series loads with its seasons, the season to play shown, and its episodes`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("series") }
            val series = model.ready { it.episodes is EpisodesState.Ready }
            assertEquals("Show", series.title)
            assertEquals(listOf(0, 1, 2), series.seasons.map { it.number })
            assertEquals(2, series.stats.seasonCount)
            assertEquals("s1", series.selectedSeasonId)
            assertEquals(listOf("e1", "e2"), (series.episodes as EpisodesState.Ready).episodes.map { it.id })
            assertEquals(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null), series.play)
        }

    @Test
    fun `choosing a season reads its episodes once`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("series") }
            model.ready { it.episodes is EpisodesState.Ready }
            model.selectSeason("s2")
            val second = model.ready { it.selectedSeasonId == "s2" && it.episodes is EpisodesState.Ready }
            assertEquals(listOf("e3"), (second.episodes as EpisodesState.Ready).episodes.map { it.id })
            val reads =
                jellyfin.requests.count {
                    it.url.encodedPath == "/Shows/series/Episodes" &&
                        it.url.queryParameter("seasonId") == "s2"
                }
            model.selectSeason("s1")
            model.selectSeason("s2")
            Thread.sleep(SETTLE_MILLIS)
            assertEquals(
                reads,
                jellyfin.requests.count {
                    it.url.encodedPath == "/Shows/series/Episodes" &&
                        it.url.queryParameter("seasonId") == "s2"
                },
            )
        }

    @Test
    fun `a season whose episodes fail says so in place, and a retry reads them again`() =
        runBlocking {
            seed()
            jellyfin.status["/Shows/series/Episodes"] = 500
            val model = viewModel().also { it.start("series") }
            val failed = model.ready { it.episodes is EpisodesState.Failed }
            assertEquals(ServerFailure.ServerError, (failed.episodes as EpisodesState.Failed).failure)
            jellyfin.status.clear()
            model.retryEpisodes()
            assertTrue(model.ready { it.episodes is EpisodesState.Ready }.episodes is EpisodesState.Ready)
        }

    @Test
    fun `a series the server refuses is a failure`() =
        runBlocking {
            seed()
            jellyfin.status["/Items/series"] = 500
            val model = viewModel().also { it.start("series") }
            assertTrue(withTimeout(TIMEOUT) { model.state.first { it is SeriesUiState.Failed } } is SeriesUiState.Failed)
            jellyfin.status.clear()
            model.retry()
            assertEquals("Show", model.ready().title)
        }

    @Test
    fun `marking an episode watched moves what to play next`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("series") }
            model.ready { it.episodes is EpisodesState.Ready }
            model.toggleEpisodeWatched("e1")
            val series =
                model.ready { page ->
                    (page.episodes as? EpisodesState.Ready)?.episodes?.first()?.watched == true &&
                        page.play is SeriesPlay.Episode &&
                        (page.play as SeriesPlay.Episode).episodeId == "e2"
                }
            assertEquals(true, (series.episodes as EpisodesState.Ready).episodes.first().watched)
        }

    @Test
    fun `the series' watched and favourite marks follow the server, and a change from another app shows`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("series") }
            model.ready()
            model.toggleFavourite()
            assertTrue(model.ready { it.favourite }.favourite)
            Thread.sleep(SETTLE_MILLIS)
            socket.emit(listOf(UserDataChange("user-1", "series", played = true)))
            assertTrue(model.ready { it.watched }.watched)
        }

    @Test
    fun `a mark the server refuses is a message and changes nothing`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("series") }
            model.ready()
            jellyfin.status["/UserPlayedItems/series"] = 500
            val event = scope.async { withTimeout(TIMEOUT) { model.events.first() } }
            Thread.sleep(SETTLE_MILLIS)
            model.toggleWatched()
            assertEquals(TitleEvent.WriteFailed(ServerFailure.ServerError), event.await())
            assertEquals(false, (model.state.value as SeriesUiState.Ready).series.watched)
        }

    private companion object {
        const val TIMEOUT = 15_000L
        const val SETTLE_MILLIS = 500L
    }
}
