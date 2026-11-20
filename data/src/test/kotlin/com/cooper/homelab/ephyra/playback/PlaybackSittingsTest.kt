package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.player.api.MediaPlayer
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlaySource
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.PlaybackOutcome
import com.cooper.homelab.player.api.PlaybackRefusal
import com.cooper.homelab.player.api.PlayerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSittingsTest {
    private val scope = CoroutineScope(Job() + UnconfinedTestDispatcher())

    @After
    fun close() = scope.cancel()

    private class Handle(
        val item: PlayItem,
    ) : PlayReportHandle

    private class FakePlayer : MediaPlayer {
        val events = Channel<PlayerEvent>(Channel.UNLIMITED)
        val played = mutableListOf<PlayRequest>()
        val updates = mutableListOf<PlayRequest>()

        override suspend fun deviceProfile() = PlaybackDeviceProfile()

        override fun play(request: PlayRequest): Flow<PlayerEvent> {
            played += request
            return events.receiveAsFlow()
        }

        override fun update(request: PlayRequest) {
            updates += request
        }
    }

    private val player = FakePlayer()
    private val episodes = (1..3).map { PlayItem("item$it", PlayTitle("Show", seasonNumber = 1, episodeNumber = it)) }
    private val minted = mutableListOf<Pair<String, Long?>>()
    private var mintFails: Throwable? = null
    private val reports = mutableListOf<Triple<String, PlayReport, Long>>()
    private var nextOf: (PlayItem) -> PlayItem? = { null }
    private var ids = 0

    private fun sittings() =
        PlaybackSittings(
            scope,
            player,
            minter = { item, _, resume ->
                mintFails?.let { throw it }
                minted += item.itemId to resume
                Minted(request(item), Handle(item))
            },
            reporter = { play, kind, position -> reports += Triple((play as Handle).item.itemId, kind, position) },
            nextEpisodes = { nextOf(it) },
            newSessionId = { "s${++ids}" },
        )

    private fun request(item: PlayItem) =
        PlayRequest(sessionId = "", source = PlaySource("https://s/${item.itemId}", "video/mp4"), title = item.title)

    private suspend fun PlaybackSittings.outcomeOf(id: String) = outcome(id).first()

    @Test
    fun `a play mints for the player's profile, hands over the request and reports what the player says`() =
        runTest {
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            assertEquals("s1", id)
            assertEquals(listOf("item1" to null), minted)
            assertEquals(id, player.played.single().sessionId)

            player.events.send(PlayerEvent.Started(0, 60_000))
            player.events.send(PlayerEvent.Progress(10_000, 60_000))
            player.events.send(PlayerEvent.Paused(12_000, 60_000))
            player.events.send(PlayerEvent.Resumed(12_000, 60_000))
            player.events.send(PlayerEvent.Stopped(20_000, 60_000))

            assertTrue(sittings.outcomeOf(id) is PlaybackOutcome.Playing)
            assertEquals(
                listOf(
                    Triple("item1", PlayReport.STARTED, 0L),
                    Triple("item1", PlayReport.PROGRESS, 10_000L),
                    Triple("item1", PlayReport.PAUSED, 12_000L),
                    Triple("item1", PlayReport.RESUMED, 12_000L),
                    Triple("item1", PlayReport.STOPPED, 20_000L),
                ),
                reports,
            )
        }

    @Test
    fun `the request names what comes next`() =
        runTest {
            nextOf = { if (it == episodes[0]) episodes[1] else null }
            sittings().start(episodes[0])
            assertEquals(episodes[1].title, player.played.single().next)
        }

    @Test
    fun `a server that refuses the mint says why, by its answer`() =
        runTest {
            val cases =
                mapOf(
                    JellyfinHttpException(401, "/Items/x/PlaybackInfo") to PlaybackRefusal.SignedOut,
                    JellyfinHttpException(403, "/Items/x/PlaybackInfo") to PlaybackRefusal.NoPermission,
                    JellyfinHttpException(404, "/Items/x/PlaybackInfo") to PlaybackRefusal.NotFound,
                    JellyfinHttpException(500, "/Items/x/PlaybackInfo") to PlaybackRefusal.Generic,
                    IOException("offline") to PlaybackRefusal.Unreachable,
                )
            cases.forEach { (failure, refusal) ->
                mintFails = failure
                val sittings = sittings()
                val outcome = sittings.outcomeOf(sittings.start(episodes[0]))
                assertEquals(failure.toString(), refusal, (outcome as PlaybackOutcome.Refused).error)
            }
            assertTrue("the player is never handed anything", player.played.isEmpty())
        }

    @Test
    fun `a retry mints again after a refusal`() =
        runTest {
            mintFails = IOException("offline")
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            mintFails = null

            sittings.retry(id)

            assertEquals(1, player.played.size)
            assertTrue(sittings.outcomeOf(id) is PlaybackOutcome.Preparing)
        }

    @Test
    fun `a rejected source is minted again once at the position reached, and a second rejection ends in an expired source`() =
        runTest {
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))

            player.events.send(PlayerEvent.SourceRejected(30_000, 403))
            assertEquals(listOf("item1" to null, "item1" to 30_000L), minted)
            assertEquals(id, player.updates.single().sessionId)

            player.events.send(PlayerEvent.SourceRejected(31_000, 403))
            assertEquals("no third mint", 2, minted.size)
            assertTrue(sittings.outcomeOf(id) is PlaybackOutcome.SourceExpired)
        }

    @Test
    fun `asking for the next episode stops this one and plays the following`() =
        runTest {
            nextOf = { item -> episodes.getOrNull(episodes.indexOf(item) + 1) }
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))

            player.events.send(PlayerEvent.NextRequested(59_000))

            assertEquals(Triple("item1", PlayReport.STOPPED, 59_000L), reports.last())
            assertEquals("item2" to 0L, minted.last())
            assertEquals(episodes[1].title, player.updates.single().title)
            assertEquals("the one after it is named for Up next", episodes[2].title, player.updates.single().next)
            assertEquals(id, player.updates.single().sessionId)

            player.events.send(PlayerEvent.Progress(5_000, 60_000))
            assertEquals(Triple("item2", PlayReport.PROGRESS, 5_000L), reports.last())
        }

    @Test
    fun `a refused next episode names that episode, and leaves the one playing reported as it was`() =
        runTest {
            nextOf = { item -> episodes.getOrNull(episodes.indexOf(item) + 1) }
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            val before = reports.size
            mintFails = JellyfinHttpException(403, "/Items/item2/PlaybackInfo")

            player.events.send(PlayerEvent.NextRequested(59_000))

            val refused = sittings.outcomeOf(id) as PlaybackOutcome.Refused
            assertEquals(episodes[1].title, refused.title)
            assertEquals(PlaybackRefusal.NoPermission, refused.error)
            assertEquals("the first is not reported stopped", before, reports.size)
        }

    @Test
    fun `a sitting that ends on its own is forgotten, and one that ends refused is kept`() =
        runTest {
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            player.events.send(PlayerEvent.Stopped(20_000, 60_000))
            player.events.close()
            assertNull(sittings.outcomeOf(id))

            mintFails = IOException("offline")
            val refusedId = sittings.start(episodes[1])
            assertTrue(sittings.outcomeOf(refusedId) is PlaybackOutcome.Refused)
        }

    @Test
    fun `asking for the next episode where there is none changes nothing`() =
        runTest {
            sittings().start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))

            player.events.send(PlayerEvent.NextRequested(59_000))

            assertTrue(player.updates.isEmpty())
            assertEquals(1, minted.size)
        }

    @Test
    fun `leaving the screen mid-play still reports where it stopped`() =
        runTest {
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            player.events.send(PlayerEvent.Progress(15_000, 60_000))

            sittings.cancel(id)

            assertEquals(Triple("item1", PlayReport.STOPPED, 15_000L), reports.last())
            assertNull(sittings.outcomeOf(id))
        }

    @Test
    fun `a report that fails does not end the play`() =
        runTest {
            val sittings =
                PlaybackSittings(
                    scope,
                    player,
                    minter = { item, _, _ -> Minted(request(item), Handle(item)) },
                    reporter = { _, _, _ -> throw IOException("offline") },
                    nextEpisodes = { null },
                    newSessionId = { "s1" },
                )
            sittings.start(episodes[0])

            player.events.send(PlayerEvent.Started(0, 60_000))
            player.events.send(PlayerEvent.Progress(10_000, 60_000))

            assertTrue(sittings.outcomeOf("s1") is PlaybackOutcome.Playing)
        }
}
