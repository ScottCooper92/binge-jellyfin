package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.player.api.MediaPlayer
import com.cooper.homelab.player.api.PlayAudioOption
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlaySource
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.PlaybackOutcome
import com.cooper.homelab.player.api.PlaybackRefusal
import com.cooper.homelab.player.api.PlayerEvent
import com.cooper.homelab.player.api.SubtitlePreference
import com.cooper.homelab.player.api.TrackChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
    private val choices = mutableListOf<StreamChoice?>()
    private var mintFails: Throwable? = null
    private var mintFailsOnce: Throwable? = null
    private var serverDefaults: Pair<TrackChoice, SubtitlePreference>? = null
    private val reports = mutableListOf<Triple<String, PlayReport, Long>>()
    private var nextOf: (PlayItem) -> PlayItem? = { null }
    private var ids = 0

    private fun sittings(offeredAudio: Boolean = false) =
        PlaybackSittings(
            scope,
            player,
            minter = { item, _, resume, choice ->
                mintFails?.let { throw it }
                mintFailsOnce?.let {
                    mintFailsOnce = null
                    throw it
                }
                minted += item.itemId to resume
                choices += choice
                Minted(withDefaults(if (offeredAudio) offered(item, choice) else request(item), choice), Handle(item))
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
    fun `the tracks the player says it is on go to a re-mint and to the next episode, not to the first mint`() =
        runTest {
            nextOf = { item -> episodes.getOrNull(episodes.indexOf(item) + 1) }
            val sittings = sittings()
            sittings.start(episodes[0])
            assertNull("a play from a page starts on the server's defaults", choices.single())
            player.events.send(PlayerEvent.Started(0, 60_000))
            val first = StreamChoice(TrackChoice("en", null), null)
            val later = StreamChoice(TrackChoice("es", "Doblaje"), TrackChoice("es", null))
            player.events.send(PlayerEvent.TracksChosen(first.subtitle, first.audio))
            player.events.send(PlayerEvent.TracksChosen(later.subtitle, later.audio))

            player.events.send(PlayerEvent.SourceRejected(30_000, 403))
            assertEquals(later, choices.last())

            player.events.send(PlayerEvent.NextRequested(59_000))
            assertEquals("item2" to 0L, minted.last())
            assertEquals(later, choices.last())
        }

    @Test
    fun `a re-mint and the next episode ask the player to open on the tracks it was on, subtitles off included`() =
        runTest {
            nextOf = { item -> episodes.getOrNull(episodes.indexOf(item) + 1) }
            val sittings = sittings()
            sittings.start(episodes[0])
            assertNull(player.played.single().preferredAudio)
            assertNull(player.played.single().preferredSubtitle)
            player.events.send(PlayerEvent.Started(0, 60_000))
            val audio = TrackChoice("es", "Doblaje", "r-7")
            val subtitle = TrackChoice("es", null)
            player.events.send(PlayerEvent.TracksChosen(subtitle, audio))

            player.events.send(PlayerEvent.SourceRejected(30_000, 403))
            assertEquals("the opaque token goes back as it came", audio, player.updates.last().preferredAudio)
            assertEquals(SubtitlePreference.Track(subtitle), player.updates.last().preferredSubtitle)

            player.events.send(PlayerEvent.TracksChosen(null, audio))
            player.events.send(PlayerEvent.NextRequested(59_000))
            assertEquals(audio, player.updates.last().preferredAudio)
            assertEquals("off is a choice, kept", SubtitlePreference.Off, player.updates.last().preferredSubtitle)
        }

    private fun withDefaults(
        request: PlayRequest,
        choice: StreamChoice?,
    ): PlayRequest =
        serverDefaults?.takeIf { choice == null }?.let { request.copy(preferredAudio = it.first, preferredSubtitle = it.second) } ?: request

    private fun offered(
        item: PlayItem,
        choice: StreamChoice?,
    ): PlayRequest {
        val current = choice?.audioStream?.toString() ?: "1"
        return request(item).copy(
            audioOptions =
                listOf(
                    PlayAudioOption("1", "en", "English", current = current == "1"),
                    PlayAudioOption("2", "es", "Spanish", current = current == "2"),
                ),
        )
    }

    @Test
    fun `a pick from the offered audio mints that stream at the position in the same session, and is remembered for the next episode`() =
        runTest {
            nextOf = { item -> episodes.getOrNull(episodes.indexOf(item) + 1) }
            val sittings = sittings(offeredAudio = true)
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))

            player.events.send(PlayerEvent.AudioOptionRequested("2", 12_000))
            assertEquals("item1" to 12_000L, minted.last())
            assertEquals(2, choices.last()?.audioStream)
            assertEquals(id, player.updates.last().sessionId)
            assertEquals(TrackChoice("es", "Spanish"), player.updates.last().preferredAudio)

            player.events.send(PlayerEvent.TracksChosen(null, TrackChoice(null, "Track 1")))
            assertEquals("the one track the transcode reports does not undo the pick", "Spanish", choices.last()?.audio?.label)

            player.events.send(PlayerEvent.NextRequested(59_000))
            assertEquals("item2" to 0L, minted.last())
            assertNull("the stream index belongs to the first episode", choices.last()?.audioStream)
            assertEquals("es", choices.last()?.audio?.language)
        }

    @Test
    fun `a switch of audio the server refuses says so, and the play goes on as it was`() =
        runTest {
            val sittings = sittings(offeredAudio = true)
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            val failures = mutableListOf<AudioSwitchFailure>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { sittings.audioSwitchFailures(id).collect { failures += it } }

            mintFailsOnce = JellyfinHttpException(409, "Items/{id}/PlaybackInfo")
            player.events.send(PlayerEvent.AudioOptionRequested("2", 12_000))
            assertEquals(listOf(AudioSwitchFailure.Refused), failures)
            assertTrue("still playing", sittings.outcomeOf(id) is PlaybackOutcome.Playing)
            assertTrue("no new source was handed over", player.updates.isEmpty())

            mintFailsOnce = IOException("down")
            player.events.send(PlayerEvent.AudioOptionRequested("2", 13_000))
            assertEquals(AudioSwitchFailure.Unreachable, failures.last())

            player.events.send(PlayerEvent.AudioOptionRequested("9", 14_000))
            assertEquals("a key it never offered is ignored", 2, failures.size)
            job.cancel()
        }

    @Test
    fun `a source that is replaced is reported so the server can end its work, and the play's state is left alone`() =
        runTest {
            val sittings = sittings(offeredAudio = true)
            sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            reports.clear()

            player.events.send(PlayerEvent.AudioOptionRequested("2", 12_000))
            assertEquals(listOf(Triple("item1", PlayReport.REPLACED, 0L)), reports)

            reports.clear()
            player.events.send(PlayerEvent.SourceRejected(30_000, 403))
            assertEquals(listOf(Triple("item1", PlayReport.REPLACED, 0L)), reports)

            reports.clear()
            mintFailsOnce = IOException("down")
            player.events.send(PlayerEvent.RetryRequested(31_000))
            assertEquals("a re-mint that fails replaces nothing", emptyList<Any>(), reports)
        }

    @Test
    fun `a play that reaches its end with nothing after it says so, and one with a next episode leaves it to Up next`() =
        runTest {
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            val endings = mutableListOf<Unit>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { sittings.endings(id).collect { endings += it } }

            player.events.send(PlayerEvent.Ended(60_000, 60_000))
            assertEquals(1, endings.size)
            player.events.send(PlayerEvent.Ended(60_000, 60_000))
            assertEquals("a replay that ends again closes it again", 2, endings.size)
            job.cancel()
        }

    @Test
    fun `an end with a next episode closes nothing`() =
        runTest {
            nextOf = { item -> episodes.getOrNull(episodes.indexOf(item) + 1) }
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            player.events.send(PlayerEvent.Started(0, 60_000))
            val endings = mutableListOf<Unit>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { sittings.endings(id).collect { endings += it } }

            player.events.send(PlayerEvent.Ended(60_000, 60_000))
            assertEquals(emptyList<Unit>(), endings)
            job.cancel()
        }

    @Test
    fun `what the mint asks the player to open on reaches the first play, and a remembered choice replaces it on a re-mint`() =
        runTest {
            val server = TrackChoice("es", "Doblaje")
            serverDefaults = server to SubtitlePreference.Off
            val sittings = sittings()
            sittings.start(episodes[0])
            assertEquals(server, player.played.single().preferredAudio)
            assertEquals(SubtitlePreference.Off, player.played.single().preferredSubtitle)
            player.events.send(PlayerEvent.Started(0, 60_000))

            player.events.send(PlayerEvent.SourceRejected(30_000, 403))
            assertEquals("no report yet, so the server's defaults stand", server, player.updates.last().preferredAudio)

            val english = TrackChoice("en", null)
            player.events.send(PlayerEvent.TracksChosen(null, english))
            player.events.send(PlayerEvent.RetryRequested(31_000))
            assertEquals("what the player says it is on wins", english, player.updates.last().preferredAudio)
        }

    @Test
    fun `a sitting run again from its start forgets the tracks`() =
        runTest {
            val sittings = sittings()
            mintFails = IOException("down")
            val id = sittings.start(episodes[0])
            mintFails = null
            sittings.retry(id)
            assertNull(choices.last())
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
    fun `a player closed before the first frame leaves no sitting behind`() =
        runTest {
            val sittings = sittings()
            val id = sittings.start(episodes[0])
            assertTrue(sittings.outcomeOf(id) is PlaybackOutcome.Preparing)

            player.events.close()

            assertNull(sittings.outcomeOf(id))
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
                    minter = { item, _, _, _ -> Minted(request(item), Handle(item)) },
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
