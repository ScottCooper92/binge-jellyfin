package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.ClientIdentity
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.MediaPlayer
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.PlaybackOutcome
import com.cooper.homelab.player.api.PlayerEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** A switch of audio through the real minter and the fake server: a pick the server does not make is not reported as made. */
class AudioSwitchTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val jellyfin = FakeJellyfin()

    @After
    fun close() {
        scope.cancel()
        jellyfin.close()
    }

    private class FakePlayer : MediaPlayer {
        val events = Channel<PlayerEvent>(Channel.UNLIMITED)
        val updates = mutableListOf<PlayRequest>()

        override suspend fun deviceProfile() =
            PlaybackDeviceProfile(videoCodecs = listOf("video/avc"), audioCodecs = listOf("audio/mp4a-latm"))

        override fun play(request: PlayRequest): Flow<PlayerEvent> = events.receiveAsFlow()

        override fun update(request: PlayRequest) {
            updates += request
        }
    }

    private val player = FakePlayer()
    private val client =
        JellyfinClient(
            OkHttpClient(),
            jellyfin.baseUrl.toHttpUrl(),
            ClientIdentity("Ephyra", "Pixel", "device-1", "1"),
            Dispatchers.IO,
            token = "token-1",
            logger = DiscardLogger,
        )
    private var ids = 0

    private fun sittings() =
        PlaybackSittings(
            scope,
            player,
            JellyfinSourceMinter(client, "user-1", JellyfinServerProfile.from("10.11.6")),
            { _, _, _ -> },
            { null },
            { "s${++ids}" },
        )

    private fun twoAudio() {
        jellyfin.playbackInfo =
            """{"PlaySessionId":"p","MediaSources":[{"Id":"source-1","Container":"mkv","SupportsDirectPlay":true,""" +
            """"SupportsDirectStream":true,""" +
            """"DefaultAudioStreamIndex":1,"MediaStreams":[""" +
            """{"Type":"Audio","Index":1,"Language":"eng","Title":"English","IsDefault":true},""" +
            """{"Type":"Audio","Index":2,"Language":"spa","Title":"Spanish"}]}]}"""
    }

    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + AWAIT_NANOS
        while (!condition() && System.nanoTime() < until) Thread.sleep(POLL_MILLIS)
        assertTrue("timed out", condition())
    }

    private fun started(sittings: PlaybackSittings): String {
        val id = sittings.start(PlayItem("movie-1", PlayTitle("Two Tongues")))
        player.events.trySend(PlayerEvent.Started(0, 60_000))
        runBlocking { sittings.outcome(id).first { it is PlaybackOutcome.Playing } }
        return id
    }

    @Test
    fun `an ask for the picked stream that fails leaves the play as it was and says so`() {
        twoAudio()
        val sittings = sittings()
        val id = started(sittings)
        val failures = CopyOnWriteArrayList<AudioSwitchFailure>()
        scope.launch { sittings.audioSwitchFailures(id).collect { failures += it } }

        jellyfin.streamPlaybackInfo = """{"ErrorCode":"NoCompatibleStream","MediaSources":[]}"""
        player.events.trySend(PlayerEvent.AudioOptionRequested("2", 12_000))

        await { failures.isNotEmpty() }
        assertEquals(listOf(AudioSwitchFailure.Refused), failures.toList())
        assertTrue("nothing was handed to the player", player.updates.isEmpty())
    }

    @Test
    fun `an ask the server answers on the picked stream is handed over as the new source`() {
        twoAudio()
        val sittings = sittings()
        started(sittings)

        jellyfin.streamPlaybackInfo = jellyfin.playbackInfo.replace("\"DefaultAudioStreamIndex\":1", "\"DefaultAudioStreamIndex\":2")
        player.events.trySend(PlayerEvent.AudioOptionRequested("2", 12_000))

        await { player.updates.isNotEmpty() }
        assertEquals(
            listOf(false, true),
            player.updates
                .single()
                .audioOptions
                .map { it.current },
        )
    }
}

private const val AWAIT_NANOS = 5_000_000_000L
private const val POLL_MILLIS = 10L
