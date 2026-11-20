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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The episode page against a fake server: the episode, then its season's episodes in place, and marks follow the server. */
class EpisodeViewModelTest {
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

    private fun seed() {
        jellyfin.browse.titles.add(FakeTitle("series", "Show", "lib-shows", "Series", year = 2020))
        jellyfin.browse.titles.add(
            FakeTitle("s1", "Season 1", "lib-shows", "Season", seriesId = "series", season = 1, extra = "\"IndexNumber\":1"),
        )
        listOf("e1", "e2", "e3").forEachIndexed { index, id ->
            jellyfin.browse.titles.add(
                FakeTitle(
                    id,
                    "Episode ${index + 1}",
                    "lib-shows",
                    "Episode",
                    seriesId = "series",
                    seriesName = "Show",
                    season = 1,
                    episode = index + 1,
                    seasonId = "s1",
                ),
            )
        }
    }

    private fun viewModel() =
        EpisodeViewModel(
            scope = scope,
            dispatcher = Dispatchers.Default,
            titles = JellyfinTitleRepository(client, "user-1", profile),
            userData = JellyfinUserDataRepository(client, "user-1", profile, socket),
            classify = { it.serverFailure() },
        )

    private suspend fun EpisodeViewModel.ready(until: (EpisodePageUi) -> Boolean = { true }): EpisodePageUi =
        (withTimeout(TIMEOUT) { state.first { it is EpisodeUiState.Ready && until(it.episode) } } as EpisodeUiState.Ready).episode

    @Test
    fun `the episode loads with its place in the series and its season's episodes`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("e2") }
            val page = model.ready { it.season is EpisodesState.Ready }
            assertEquals("Episode 2", page.title)
            assertEquals("Show", page.seriesName)
            assertEquals(1, page.seasonNumber)
            assertEquals(2, page.episodeNumber)
            assertEquals(PlayAction(PlayKind.Play, null), page.play)
            assertEquals(listOf("e1", "e2", "e3"), (page.season as EpisodesState.Ready).episodes.map { it.id })
        }

    @Test
    fun `an episode the server gives no season for has no season row`() =
        runBlocking {
            seed()
            jellyfin.browse.titles.add(
                FakeTitle("loose", "Loose", "lib-shows", "Episode", seriesId = "series", seriesName = "Show", season = 1, episode = 9),
            )
            val model = viewModel().also { it.start("loose") }
            val page = model.ready()
            assertNull(page.season)
            Thread.sleep(SETTLE_MILLIS)
            assertNull(model.ready().season)
        }

    @Test
    fun `a season that fails says so in place and a retry reads it again`() =
        runBlocking {
            seed()
            jellyfin.status["/Shows/series/Episodes"] = 500
            val model = viewModel().also { it.start("e1") }
            val failed = model.ready { it.season is EpisodesState.Failed }
            assertEquals(ServerFailure.ServerError, (failed.season as EpisodesState.Failed).failure)
            jellyfin.status.clear()
            model.retrySeason()
            assertTrue(model.ready { it.season is EpisodesState.Ready }.season is EpisodesState.Ready)
        }

    @Test
    fun `an episode the server refuses is a failure and a retry reads it again`() =
        runBlocking {
            seed()
            jellyfin.status["/Items/e1"] = 500
            val model = viewModel().also { it.start("e1") }
            assertTrue(withTimeout(TIMEOUT) { model.state.first { it is EpisodeUiState.Failed } } is EpisodeUiState.Failed)
            jellyfin.status.clear()
            model.retry()
            assertEquals("Episode 1", model.ready().title)
        }

    @Test
    fun `watched and favourite follow the server and a change from another app shows`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("e1") }
            model.ready()
            model.toggleFavourite()
            assertTrue(model.ready { it.favourite }.favourite)
            Thread.sleep(SETTLE_MILLIS)
            socket.emit(listOf(UserDataChange("user-1", "e1", played = true)))
            val page = model.ready { it.watched && (it.season as? EpisodesState.Ready)?.episodes?.first()?.watched == true }
            assertTrue(page.watched)
        }

    @Test
    fun `a mark the server refuses is a message and changes nothing`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("e1") }
            model.ready()
            jellyfin.status["/UserPlayedItems/e1"] = 500
            val event = scope.async { withTimeout(TIMEOUT) { model.events.first() } }
            Thread.sleep(SETTLE_MILLIS)
            model.toggleWatched()
            assertEquals(TitleEvent.WriteFailed(ServerFailure.ServerError), event.await())
            assertEquals(false, (model.state.value as EpisodeUiState.Ready).episode.watched)
        }

    private companion object {
        const val TIMEOUT = 10_000L
        const val SETTLE_MILLIS = 300L
    }
}
