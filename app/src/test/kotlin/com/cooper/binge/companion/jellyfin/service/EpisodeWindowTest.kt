package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.EpisodeRef
import com.binge.companion.contracts.library.v1.EpisodeWatchState
import com.binge.companion.contracts.library.v1.WatchState
import com.google.protobuf.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order and size of the window `WatchState.episodes` carries. */
class EpisodeWindowTest {
    private fun entry(
        season: Int,
        episode: Int,
        lastPlayedSeconds: Long? = null,
    ): EpisodeWatchState =
        EpisodeWatchState
            .newBuilder()
            .setEpisode(EpisodeRef.newBuilder().setSeasonNumber(season).setEpisodeNumber(episode))
            .setState(
                WatchState.newBuilder().apply {
                    lastPlayedSeconds?.let { lastPlayed = Timestamp.newBuilder().setSeconds(it).build() }
                },
            ).build()

    private fun List<EpisodeWatchState>.ids() = map { it.episode.seasonNumber to it.episode.episodeNumber }

    @Test
    fun `most recently played first, then the unplayed in season and episode order`() {
        val window =
            listOf(entry(1, 1), entry(1, 2, 100), entry(2, 1), entry(2, 2, 300), entry(1, 3, 200)).shuffled().episodeWindow()
        assertEquals(listOf(2 to 2, 1 to 3, 1 to 2, 1 to 1, 2 to 1), window.ids())
    }

    @Test
    fun `an exact tie keeps season then episode order`() {
        val window = listOf(entry(2, 1, 50), entry(1, 2, 50), entry(1, 1, 50)).shuffled().episodeWindow()
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1), window.ids())
    }

    @Test
    fun `a window is cut at 100, and an unplayed episode never displaces a played one`() {
        val unplayed = (1..150).map { entry(1, it) }
        val played = entry(3, 1, 10)
        val window = (unplayed + played).episodeWindow()
        assertEquals(EPISODE_WINDOW, window.size)
        assertEquals(3 to 1, window.first().let { it.episode.seasonNumber to it.episode.episodeNumber })
        assertEquals(1 to 99, window.last().let { it.episode.seasonNumber to it.episode.episodeNumber })
    }

    @Test
    fun `a short list is kept whole, and an empty one stays empty`() {
        assertEquals(3, listOf(entry(1, 1), entry(1, 2), entry(1, 3)).episodeWindow().size)
        assertEquals(emptyList<EpisodeWatchState>(), emptyList<EpisodeWatchState>().episodeWindow())
    }
}
