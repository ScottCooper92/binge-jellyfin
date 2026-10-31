package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.AvailabilityState
import com.binge.companion.contracts.library.v1.Capability
import com.binge.companion.contracts.library.v1.EpisodeRef
import com.binge.companion.contracts.library.v1.GetAvailabilityRequest
import com.binge.companion.contracts.library.v1.GetPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.HandshakeRequest
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.library.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.library.v1.ListNextUpRequest
import com.binge.companion.contracts.library.v1.ObserveAvailabilityRequest
import com.binge.companion.contracts.library.v1.ObserveWatchStateRequest
import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import com.binge.companion.contracts.library.v1.PlaybackEvent
import com.binge.companion.contracts.library.v1.PlaybackMethod
import com.binge.companion.contracts.library.v1.ReportPlaybackRequest
import com.binge.companion.contracts.library.v1.SetPlayedRequest
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.userMessageOf
import com.cooper.binge.companion.jellyfin.FakeItem
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** The whole LIBRARY contract over an in-process channel, against a fake Jellyfin server. */
@RunWith(RobolectricTestRunner::class)
class JellyfinLibraryServiceTest {
    private val jellyfin = FakeJellyfin()
    private val sessions: SessionStore = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Binge Jellyfin", "Test", "1.2.3", Dispatchers.IO)
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
                    playback = PlaybackSources(now = { Instant.ofEpochSecond(1_000) }),
                    versionName = "1.2.3",
                    messages = messages,
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
                FakeItem("ep-1", "Good News About Hell", "Episode", seriesId = "series-1", season = 1, episode = 1, played = true),
                FakeItem("ep-2", "Half Loop", "Episode", seriesId = "series-1", season = 1, episode = 2),
                FakeItem("ep-3", "Missing", "Episode", seriesId = "series-1", season = 1, episode = 3, locationType = "Virtual"),
                FakeItem("untagged", "Home Video", "Movie"),
            )
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        channel.shutdownNow()
        server.shutdownNow()
        jellyfin.close()
    }

    private val movie = media(MediaType.MEDIA_TYPE_MOVIE, 1084199)
    private val series = media(MediaType.MEDIA_TYPE_TV, 95396)

    private fun media(
        type: MediaType,
        id: Int,
    ) = MediaId
        .newBuilder()
        .setMediaType(type)
        .setTmdbId(id)
        .build()

    private fun episode(
        season: Int,
        number: Int,
    ) = EpisodeRef
        .newBuilder()
        .setSeasonNumber(season)
        .setEpisodeNumber(number)
        .build()

    private fun statusOf(block: suspend () -> Unit): StatusException =
        runBlocking {
            try {
                block()
                error("expected a failure")
            } catch (e: StatusException) {
                e
            }
        }

    @Test
    fun `handshake declares the capabilities this app serves, without PLAY`() =
        runBlocking {
            val response = stub.handshake(HandshakeRequest.getDefaultInstance())
            assertEquals("Jellyfin", response.providerName)
            assertEquals("1.2.3", response.companionVersionName)
            assertTrue(Capability.CAPABILITY_PLAYBACK_SOURCE in response.capabilitiesList)
            assertTrue(Capability.CAPABILITY_PLAYBACK_PROGRESS in response.capabilitiesList)
            assertFalse(Capability.CAPABILITY_PLAY in response.capabilitiesList)
        }

    @Test
    fun `a movie the server holds is in the library, one it does not is an answer not a failure`() =
        runBlocking {
            val held = stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()).availability
            assertEquals(AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY, held.state)
            assertEquals("movie-1", held.itemId)

            val absent = stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE, 1)).build())
            assertEquals(AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY, absent.availability.state)
        }

    @Test
    fun `a series lists the episodes the server has files for, by season`() =
        runBlocking {
            val availability = stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(series).build()).availability
            assertEquals(1, availability.seasonsCount)
            assertEquals(listOf(1, 2), availability.getSeasons(0).episodeNumbersList)
        }

    @Test
    fun `a movie id typed as a series is not found among the series`() =
        runBlocking {
            val response =
                stub.getAvailability(
                    GetAvailabilityRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_TV, 1084199)).build(),
                )
            assertEquals(AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY, response.availability.state)
        }

    @Test
    fun `an unusable media id is INVALID_ARGUMENT`() {
        val failure =
            statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE, 0)).build()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, failure.status.code)
        val unspecified =
            statusOf {
                stub.getAvailability(
                    GetAvailabilityRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_UNSPECIFIED, 5)).build(),
                )
            }
        assertEquals(Status.Code.INVALID_ARGUMENT, unspecified.status.code)
    }

    @Test
    fun `signed out is UNAUTHENTICATED with a sentence for the user`() {
        runBlocking { sessions.clear() }
        val failure = statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()) }
        assertEquals(Status.Code.UNAUTHENTICATED, failure.status.code)
        assertEquals("Sign in to Jellyfin", userMessageOf(failure.trailers)?.message)
    }

    @Test
    fun `a token the server refuses is UNAUTHENTICATED`() {
        jellyfin.status["/Items"] = 401
        val failure = statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()) }
        assertEquals(Status.Code.UNAUTHENTICATED, failure.status.code)
    }

    @Test
    fun `a server error is UNAVAILABLE and a refusal PERMISSION_DENIED`() {
        jellyfin.status["/Items"] = 500
        assertEquals(
            Status.Code.UNAVAILABLE,
            statusOf {
                stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build())
            }.status.code,
        )
        jellyfin.status["/Items"] = 403
        assertEquals(
            Status.Code.PERMISSION_DENIED,
            statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()) }.status.code,
        )
    }

    @Test
    fun `a server that cannot be reached is UNAVAILABLE with a sentence for the user`() {
        jellyfin.close()
        val failure = statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()) }
        assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
        assertEquals("Can't reach Jellyfin", userMessageOf(failure.trailers)?.message)
    }

    @Test
    fun `a movie's watch state carries its position and when it was last played`() =
        runBlocking {
            val state = stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(movie).build()).state
            assertFalse(state.played)
            assertEquals(526_000L, state.positionMillis)
            assertEquals(3_600_000L, state.runtimeMillis)
            assertEquals(Instant.parse("2026-10-30T22:31:04Z").epochSecond, state.lastPlayed.seconds)
        }

    @Test
    fun `a series' watch state lists each episode`() =
        runBlocking {
            val state = stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(series).build()).state
            assertEquals(2, state.episodesCount)
            assertTrue(
                state.episodesList
                    .first { it.episode.episodeNumber == 1 }
                    .state.played,
            )
            assertFalse(
                state.episodesList
                    .first { it.episode.episodeNumber == 2 }
                    .state.played,
            )
        }

    @Test
    fun `the watch state of a title the server does not hold is NOT_FOUND`() {
        val failure =
            statusOf { stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE, 7)).build()) }
        assertEquals(Status.Code.NOT_FOUND, failure.status.code)
    }

    @Test
    fun `SetPlayed marks the episode the request names`() =
        runBlocking {
            stub.setPlayed(
                SetPlayedRequest
                    .newBuilder()
                    .setMedia(series)
                    .setEpisode(episode(1, 2))
                    .setPlayed(true)
                    .build(),
            )
            assertTrue(jellyfin.items.first { it.id == "ep-2" }.played)
            stub.setPlayed(
                SetPlayedRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setPlayed(false)
                    .build(),
            )
            assertEquals(1, jellyfin.requests.count { it.method == "DELETE" })
        }

    @Test
    fun `a direct play source names the file, carries the token in a header and not the URL, and resumes`() =
        runBlocking {
            val response =
                stub.getPlaybackSource(
                    GetPlaybackSourceRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setDevice(PlaybackDeviceProfile.newBuilder().addVideoCodecs("video/avc").addAudioCodecs("audio/mp4a-latm"))
                        .build(),
                )
            val source = response.source
            assertTrue(source.url.startsWith("${jellyfin.baseUrl}/Videos/movie-1/stream?static=true&mediaSourceId=source-1"))
            assertFalse(source.url.contains("ApiKey"))
            assertTrue(
                source.headersList
                    .single { it.name == "Authorization" }
                    .value
                    .contains("Token=\"token-1\""),
            )
            assertEquals("video/mp4", source.mimeType)
            assertEquals(1_000L + 6 * 60 * 60, source.expiresAt.seconds)
            assertEquals(PlaybackMethod.PLAYBACK_METHOD_DIRECT_PLAY, response.method)
            assertEquals(526_000L, response.resumePositionMillis)
            assertEquals(3_600_000L, response.runtimeMillis)
            assertEquals("movie-1|source-1|play-1|DirectPlay", response.playbackSessionId)
            // The external WebVTT track, with the token the header-less subtitle fetch needs; the embedded one is not offered.
            val subtitle = response.subtitlesList.single()
            assertEquals("text/vtt", subtitle.mimeType)
            assertTrue(subtitle.url.endsWith("Stream.vtt?ApiKey=token-1"))
            assertTrue(subtitle.hearingImpaired)
            // The profile the server chose from is the host's, translated.
            val profile = jellyfin.lastBody("/Items/movie-1/PlaybackInfo")["DeviceProfile"].toString()
            assertTrue(profile.contains("\"VideoCodec\":\"h264\""))
        }

    @Test
    fun `an installed player's source carries the token in the URL`() =
        runBlocking {
            val response =
                stub.getPlaybackSource(
                    GetPlaybackSourceRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setPlayerPackage("org.videolan.vlc")
                        .build(),
                )
            assertTrue(response.source.url.endsWith("ApiKey=token-1"))
        }

    @Test
    fun `a transcode is HLS from the server's own URL`() =
        runBlocking {
            jellyfin.playbackInfo = FakeJellyfin.transcodeInfo()
            val response =
                stub.getPlaybackSource(
                    GetPlaybackSourceRequest
                        .newBuilder()
                        .setMedia(series)
                        .setEpisode(episode(1, 2))
                        .build(),
                )
            // The server puts the token into its transcode URL; Binge's own player gets it in the header only.
            assertEquals("${jellyfin.baseUrl}/videos/x/master.m3u8?MediaSourceId=source-2", response.source.url)
            assertFalse(response.source.url.contains("ApiKey", ignoreCase = true))
            assertEquals("application/x-mpegURL", response.source.mimeType)
            assertEquals(PlaybackMethod.PLAYBACK_METHOD_TRANSCODE, response.method)
            assertEquals("ep-2|source-2|play-2|Transcode", response.playbackSessionId)
        }

    @Test
    fun `an installed player's transcode source carries the token in the URL`() =
        runBlocking {
            jellyfin.playbackInfo = FakeJellyfin.transcodeInfo()
            val response =
                stub.getPlaybackSource(
                    GetPlaybackSourceRequest
                        .newBuilder()
                        .setMedia(series)
                        .setEpisode(episode(1, 2))
                        .setPlayerPackage("org.videolan.vlc")
                        .build(),
                )
            assertEquals("${jellyfin.baseUrl}/videos/x/master.m3u8?MediaSourceId=source-2&ApiKey=token-1", response.source.url)
        }

    @Test
    fun `a signed-out handshake is UNAUTHENTICATED with a message for the user`() {
        runBlocking { sessions.clear() }
        val failure = statusOf { stub.handshake(HandshakeRequest.getDefaultInstance()) }
        assertEquals(Status.Code.UNAUTHENTICATED, failure.status.code)
        assertEquals("Sign in to Jellyfin", userMessageOf(failure.trailers)?.message)
    }

    @Test
    fun `a report with an episode on a movie, or without one on a series, is INVALID_ARGUMENT`() {
        fun report(
            media: MediaId,
            episode: EpisodeRef?,
        ) = ReportPlaybackRequest
            .newBuilder()
            .setMedia(media)
            .setPlaybackSessionId("movie-1|source-1|play-1|DirectPlay")
            .setEvent(PlaybackEvent.PLAYBACK_EVENT_PROGRESS)
            .apply { episode?.let(::setEpisode) }
            .build()
        assertEquals(Status.Code.INVALID_ARGUMENT, statusOf { stub.reportPlayback(report(movie, episode(1, 1))) }.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, statusOf { stub.reportPlayback(report(series, null)) }.status.code)
    }

    @Test
    fun `a page_token this app did not issue is INVALID_ARGUMENT`() {
        for (token in listOf("x", "p", "p-1", "12")) {
            val resume = statusOf { stub.listContinueWatching(ListContinueWatchingRequest.newBuilder().setPageToken(token).build()) }
            assertEquals(token, Status.Code.INVALID_ARGUMENT, resume.status.code)
        }
        val nextUp = statusOf { stub.listNextUp(ListNextUpRequest.newBuilder().setPageToken("x").build()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, nextUp.status.code)
    }

    @Test
    fun `continue watching pages through the whole list, and the last page carries no token`() =
        runBlocking {
            jellyfin.items += (2..3).map { FakeItem("movie-$it", "Movie $it", "Movie", tmdbId = 2000 + it, positionTicks = 10_000_000L) }
            val first = stub.listContinueWatching(ListContinueWatchingRequest.newBuilder().setPageSize(2).build())
            assertEquals(2, first.entriesCount)
            assertEquals("p2", first.nextPageToken)
            val last =
                stub.listContinueWatching(
                    ListContinueWatchingRequest
                        .newBuilder()
                        .setPageSize(2)
                        .setPageToken(first.nextPageToken)
                        .build(),
                )
            assertEquals(1, last.entriesCount)
            assertEquals("", last.nextPageToken)
            val all = (first.entriesList + last.entriesList).map { it.media.tmdbId }.toSet()
            assertEquals(setOf(1084199, 2002, 2003), all)
        }

    @Test
    fun `a series without an episode, or a movie with one, is INVALID_ARGUMENT`() {
        val noEpisode = statusOf { stub.getPlaybackSource(GetPlaybackSourceRequest.newBuilder().setMedia(series).build()) }
        assertEquals(Status.Code.INVALID_ARGUMENT, noEpisode.status.code)
        val withEpisode =
            statusOf {
                stub.getPlaybackSource(
                    GetPlaybackSourceRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setEpisode(episode(1, 1))
                        .build(),
                )
            }
        assertEquals(Status.Code.INVALID_ARGUMENT, withEpisode.status.code)
    }

    @Test
    fun `an episode the server does not have is NOT_FOUND`() {
        val failure =
            statusOf {
                stub.getPlaybackSource(
                    GetPlaybackSourceRequest
                        .newBuilder()
                        .setMedia(series)
                        .setEpisode(episode(2, 1))
                        .build(),
                )
            }
        assertEquals(Status.Code.NOT_FOUND, failure.status.code)
    }

    @Test
    fun `a server that refuses playback is FAILED_PRECONDITION`() {
        jellyfin.playbackInfo = """{"ErrorCode":"NotAllowed","MediaSources":[]}"""
        assertEquals(
            Status.Code.FAILED_PRECONDITION,
            statusOf { stub.getPlaybackSource(GetPlaybackSourceRequest.newBuilder().setMedia(movie).build()) }.status.code,
        )
        jellyfin.playbackInfo = """{"MediaSources":[]}"""
        assertEquals(
            Status.Code.NOT_FOUND,
            statusOf {
                stub.getPlaybackSource(GetPlaybackSourceRequest.newBuilder().setMedia(movie).build())
            }.status.code,
        )
    }

    @Test
    fun `each report goes to the matching Jellyfin session call, in ticks`() =
        runBlocking {
            fun report(event: PlaybackEvent) =
                ReportPlaybackRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setPlaybackSessionId("movie-1|source-1|play-1|DirectPlay")
                    .setEvent(event)
                    .setPositionMillis(1_500)
                    .setRuntimeMillis(3_600_000)
                    .build()
            stub.reportPlayback(report(PlaybackEvent.PLAYBACK_EVENT_STARTED))
            assertEquals(JsonPrimitive(15_000_000L), jellyfin.lastBody("/Sessions/Playing")["PositionTicks"])
            assertEquals(JsonPrimitive("play-1"), jellyfin.lastBody("/Sessions/Playing")["PlaySessionId"])

            stub.reportPlayback(report(PlaybackEvent.PLAYBACK_EVENT_PAUSED))
            assertEquals(JsonPrimitive(true), jellyfin.lastBody("/Sessions/Playing/Progress")["IsPaused"])
            stub.reportPlayback(report(PlaybackEvent.PLAYBACK_EVENT_PROGRESS))
            stub.reportPlayback(report(PlaybackEvent.PLAYBACK_EVENT_RESUMED))
            assertEquals(3, jellyfin.hits("/Sessions/Playing/Progress"))

            stub.reportPlayback(report(PlaybackEvent.PLAYBACK_EVENT_STOPPED))
            assertEquals(JsonPrimitive(15_000_000L), jellyfin.lastBody("/Sessions/Playing/Stopped")["PositionTicks"])
            // Finished reports the end, so the server marks it played.
            stub.reportPlayback(report(PlaybackEvent.PLAYBACK_EVENT_FINISHED))
            assertEquals(JsonPrimitive(36_000_000_000L), jellyfin.lastBody("/Sessions/Playing/Stopped")["PositionTicks"])
        }

    @Test
    fun `a report for a session this app never minted is INVALID_ARGUMENT`() {
        val failure =
            statusOf {
                stub.reportPlayback(
                    ReportPlaybackRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setPlaybackSessionId("nope")
                        .setEvent(PlaybackEvent.PLAYBACK_EVENT_STARTED)
                        .build(),
                )
            }
        assertEquals(Status.Code.INVALID_ARGUMENT, failure.status.code)
        val unknownEvent =
            statusOf {
                stub.reportPlayback(
                    ReportPlaybackRequest
                        .newBuilder()
                        .setMedia(movie)
                        .setPlaybackSessionId("movie-1|source-1|play-1|DirectPlay")
                        .build(),
                )
            }
        assertEquals(Status.Code.INVALID_ARGUMENT, unknownEvent.status.code)
    }

    @Test
    fun `continue watching and next up name titles by TMDB id`() =
        runBlocking {
            val resume = stub.listContinueWatching(ListContinueWatchingRequest.getDefaultInstance()).entriesList
            assertEquals(movie, resume.single().media)
            assertTrue(resume.single().artworkUrl.contains("/Items/movie-1/Images/Primary"))

            val next = stub.listNextUp(ListNextUpRequest.newBuilder().setPageSize(500).build()).entriesList.single()
            assertEquals(series, next.media)
            assertEquals(2, next.episode.episodeNumber)
        }

    @Test
    fun `the observe streams start with the current state`() =
        runBlocking {
            val availability = stub.observeAvailability(ObserveAvailabilityRequest.newBuilder().setMedia(movie).build()).first()
            assertEquals(AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY, availability.availability.state)
            val state = stub.observeWatchState(ObserveWatchStateRequest.newBuilder().setMedia(movie).build()).first()
            assertEquals(526_000L, state.state.positionMillis)
        }

    @Test
    fun `an answer that is not JSON, a captive portal's page say, is UNAVAILABLE with a sentence for the user`() {
        jellyfin.notJson += "/Items"
        val failure = statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()) }
        assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
        assertEquals("Can't reach Jellyfin", userMessageOf(failure.trailers)?.message)
    }
}
