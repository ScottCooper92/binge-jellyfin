package com.cooper.homelab.ephyra.service

import com.binge.companion.contracts.library.v1.AvailabilityState
import com.binge.companion.contracts.library.v1.GetAvailabilityRequest
import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.HandshakeRequest
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.library.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.cooper.homelab.ephyra.FakeItem
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.server.ServerEvent
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.concurrent.TimeUnit

/** The exported Service for a plain user, against a fake server that refuses them every administrator's route. */
@RunWith(RobolectricTestRunner::class)
class NonAdminServiceTest {
    private val jellyfin = FakeJellyfin().also { it.enforcesAdministrator = true }
    private val sessions: SessionStore = testSessionStore()
    private val connection =
        Connection(
            OkHttpClient.Builder().readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
            sessions,
            "Jellyfin Companion",
            "Test",
            "1.2.3",
            Dispatchers.IO,
            DiscardLogger,
        )
    private val messages =
        object : UserMessages {
            override fun signedOut() = "Sign in to Jellyfin"

            override fun unreachable() = "Can't reach Jellyfin"

            override fun locale() = "en-GB"
        }
    private val name = InProcessServerBuilder.generateName()
    private val server: Server =
        InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(
                JellyfinLibraryService(
                    reads = LibraryReads(connection, LibraryIndex(), messages),
                    playback = PlaybackSources(now = { Instant.ofEpochSecond(1_000) }, logger = DiscardLogger),
                    versionName = "1.2.3",
                    messages = messages,
                    events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 8),
                    logger = DiscardLogger,
                ),
            ).build()
            .start()
    private val channel: ManagedChannel = InProcessChannelBuilder.forName(name).directExecutor().build()
    private val stub = LibraryServiceGrpcKt.LibraryServiceCoroutineStub(channel)

    @Before
    fun signIn() {
        jellyfin.items +=
            listOf(
                FakeItem(
                    "movie-1",
                    "Companion",
                    "Movie",
                    tmdbId = 1084199,
                    positionTicks = 5_260_000_000L,
                    lastPlayed = "2026-10-30T22:31:04Z",
                ),
                FakeItem("series-1", "Severance", "Series", tmdbId = 95396),
                FakeItem("ep-1", "Good News About Hell", "Episode", seriesId = "series-1", season = 1, episode = 1),
            )
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        channel.shutdownNow()
        server.shutdownNow()
        connection.close()
        jellyfin.close()
    }

    private val movie =
        MediaId
            .newBuilder()
            .setMediaType(MediaType.MEDIA_TYPE_MOVIE)
            .setTmdbId(1084199)
            .build()

    @Test
    fun `a plain user's handshake, availability, watch state and continue watching are all answered`() =
        runBlocking {
            assertEquals("Jellyfin", stub.handshake(HandshakeRequest.getDefaultInstance()).providerName)
            val held = stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()).availability
            assertEquals(AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY, held.state)
            val watch = stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(movie).build()).state
            assertEquals(526_000L, watch.positionMillis)
            assertEquals(1, stub.listContinueWatching(ListContinueWatchingRequest.getDefaultInstance()).entriesCount)
            assertEquals(emptyList<String>(), jellyfin.forbidden.toList())
        }

    private companion object {
        const val CALL_TIMEOUT_SECONDS = 10L
    }
}
