package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.EpisodeWatchState
import com.binge.companion.contracts.library.v1.WatchState
import io.grpc.Status
import io.grpc.StatusException
import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeWatchPagesTest {
    private fun entry(
        season: Int,
        number: Int,
        played: Boolean = true,
        position: Long = 0,
    ) = EpisodeWatchState
        .newBuilder()
        .setEpisode(episodeRef(season, number))
        .setState(WatchState.newBuilder().setPlayed(played).setPositionMillis(position))
        .build()

    private fun keys(page: EpisodeWatchPage) = page.episodes.map { it.episode.seasonNumber to it.episode.episodeNumber }

    private fun invalid(block: () -> Unit) {
        try {
            block()
            error("expected INVALID_ARGUMENT")
        } catch (e: StatusException) {
            assertEquals(Status.Code.INVALID_ARGUMENT, e.status.code)
        }
    }

    /** Three seasons of 100, in reverse order, so the sort has work to do; specials (season 0) last in the input. */
    private val all = (3 downTo 1).flatMap { s -> (100 downTo 1).map { entry(s, it) } } + entry(0, 1)

    private fun walk(size: Int): List<EpisodeWatchPage> {
        val pages = mutableListOf<EpisodeWatchPage>()
        var token = ""
        do {
            val page = all.episodePage(token, size)
            pages += page
            token = page.nextPageToken
        } while (token.isNotEmpty())
        return pages
    }

    @Test
    fun `pages ascend by season then episode with the specials first and cover every episode once`() {
        val pages = walk(size = 100)
        assertEquals(listOf(100, 100, 100, 1), pages.map { it.episodes.size })
        val flat = pages.flatMap(::keys)
        assertEquals(0 to 1, flat.first())
        assertEquals(flat.sortedWith(compareBy({ it.first }, { it.second })), flat)
        assertEquals(301, flat.distinct().size)
        assertEquals("", pages.last().nextPageToken)
    }

    @Test
    fun `no page size gives the default of 100 and a larger one is cut to 200`() {
        assertEquals(100, all.episodePage("", 0).episodes.size)
        assertEquals(200, all.episodePage("", 1000).episodes.size)
        assertEquals(50, all.episodePage("", 50).episodes.size)
    }

    @Test
    fun `an episode with no state is left out, a resume point counts`() {
        val page = listOf(entry(1, 1), entry(1, 2, played = false), entry(1, 3, played = false, position = 5)).episodePage("", 0)
        assertEquals(listOf(1 to 1, 1 to 3), keys(page))
    }

    @Test
    fun `the last page has no token, even when it is full`() {
        assertEquals("", listOf(entry(1, 1), entry(1, 2)).episodePage("", 2).nextPageToken)
        assertEquals("", emptyList<EpisodeWatchState>().episodePage("", 0).nextPageToken)
    }

    @Test
    fun `a token holds its place when an earlier episode gains state between pages`() {
        val first = all.episodePage("", 100)
        val grown = all + entry(0, 2) + entry(1, 0)
        val second = grown.episodePage(first.nextPageToken, 100)
        // The first page ended at season 1 episode 99; the new episodes sort before it, so nothing is repeated or skipped.
        assertEquals(1 to 100, keys(second).first())
        assertEquals(100, second.episodes.size)
    }

    @Test
    fun `a token is valid only with the size it was issued under, and nothing else is`() {
        val token = all.episodePage("", 50).nextPageToken
        invalid { all.episodePage(token, 51) }
        invalid { all.episodePage(token, 0) }
        invalid { all.episodePage("p1s50", 50) }
        invalid { all.episodePage("garbage", 50) }
        invalid { all.episodePage("k99999999999e1s50", 50) }
    }
}
