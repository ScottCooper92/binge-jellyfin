package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
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

/** The season page against a fake server: the season, then its episodes in place, and marks follow the server. */
class SeasonViewModelTest {
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
            FakeTitle(
                "s1",
                "Season 1",
                "lib-shows",
                "Season",
                seriesId = "series",
                seriesName = "Show",
                season = 1,
                extra = "\"IndexNumber\":1",
            ),
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
        SeasonViewModel(
            scope = scope,
            dispatcher = Dispatchers.Default,
            titles = JellyfinTitleRepository(client, "user-1", profile),
            userData = JellyfinUserDataRepository(client, "user-1", profile, socket),
            classify = { it.serverFailure() },
        )

    private suspend fun SeasonViewModel.ready(until: (SeasonPageUi) -> Boolean = { true }): SeasonPageUi =
        (withTimeout(TIMEOUT) { state.first { it is SeasonUiState.Ready && until(it.season) } } as SeasonUiState.Ready).season

    @Test
    fun `the season loads with its episodes and the one to play`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("s1") }
            val page = model.ready { it.episodes is EpisodesState.Ready }
            assertEquals("Season 1", page.name)
            assertEquals(1, page.seasonNumber)
            assertEquals("Show", page.seriesName)
            assertEquals(listOf("e1", "e2", "e3"), (page.episodes as EpisodesState.Ready).episodes.map { it.id })
            assertEquals(SeriesPlay.Episode(PlayKind.Play, "e1", 1, 1, null), page.play)
        }

    @Test
    fun `marking an episode watched moves the one to play`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("s1") }
            model.ready { it.episodes is EpisodesState.Ready }
            model.toggleEpisodeWatched("e1")
            val page = model.ready { (it.play as? SeriesPlay.Episode)?.episodeId == "e2" }
            assertEquals(true, (page.episodes as EpisodesState.Ready).episodes.first().watched)
        }

    @Test
    fun `episodes that fail say so in place and a retry reads them again`() =
        runBlocking {
            seed()
            jellyfin.status["/Shows/series/Episodes"] = 500
            val model = viewModel().also { it.start("s1") }
            val failed = model.ready { it.episodes is EpisodesState.Failed }
            assertEquals(ServerFailure.ServerError, (failed.episodes as EpisodesState.Failed).failure)
            // Nothing is known about what to play, which is not the same as there being no episode.
            assertNull(failed.play)
            jellyfin.status.clear()
            model.retryEpisodes()
            assertTrue(model.ready { it.episodes is EpisodesState.Ready }.episodes is EpisodesState.Ready)
        }

    @Test
    fun `a season the server refuses is a failure and a retry reads it again`() =
        runBlocking {
            seed()
            jellyfin.status["/Items/s1"] = 500
            val model = viewModel().also { it.start("s1") }
            assertTrue(withTimeout(TIMEOUT) { model.state.first { it is SeasonUiState.Failed } } is SeasonUiState.Failed)
            jellyfin.status.clear()
            model.retry()
            assertEquals("Season 1", model.ready().name)
        }

    @Test
    fun `the season's watched mark follows the server and a refusal is a message`() =
        runBlocking {
            seed()
            val model = viewModel().also { it.start("s1") }
            model.ready()
            model.toggleWatched()
            assertTrue(model.ready { it.watched }.watched)
            Thread.sleep(SETTLE_MILLIS)
            jellyfin.status["/UserPlayedItems/s1"] = 500
            val event = scope.async { withTimeout(TIMEOUT) { model.events.first() } }
            Thread.sleep(SETTLE_MILLIS)
            model.toggleWatched()
            assertEquals(TitleEvent.WriteFailed(ServerFailure.ServerError), event.await())
        }

    private fun episode(
        id: String,
        number: Int,
        played: Boolean = false,
        positionTicks: Long = 0,
    ) = BrowseItem(
        id,
        "Episode $number",
        ItemKind.Episode,
        seasonNumber = 1,
        episodeNumber = number,
        userData = ItemUserData(played = played, positionTicks = positionTicks),
    )

    @Test
    fun `the season's button resumes, plays the first not watched, or offers the first again`() {
        val e1 = episode("e1", 1, played = true)
        val e2 = episode("e2", 2)
        val e3 = episode("e3", 3, positionTicks = 600_000_000L)
        assertEquals("e3", (seasonPlay(listOf(e1, e2, e3)) as SeriesPlay.Episode).episodeId)
        assertEquals("e2", (seasonPlay(listOf(e1, e2)) as SeriesPlay.Episode).episodeId)
        val again = seasonPlay(listOf(e1, episode("e2b", 2, played = true))) as SeriesPlay.AllWatched
        assertEquals("e1", again.first.episodeId)
        assertEquals(PlayKind.Play, again.first.kind)
        assertEquals(SeriesPlay.NoEpisodes, seasonPlay(emptyList()))
    }

    private companion object {
        const val TIMEOUT = 10_000L
        const val SETTLE_MILLIS = 300L
    }
}
