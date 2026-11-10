package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.AvailabilityReason
import com.binge.companion.contracts.library.v1.AvailabilityState
import com.binge.companion.contracts.library.v1.Capability
import com.binge.companion.contracts.library.v1.EpisodeRef
import com.binge.companion.contracts.library.v1.GetAvailabilityRequest
import com.binge.companion.contracts.library.v1.GetPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.HandshakeRequest
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.library.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.library.v1.ListEpisodeWatchStateRequest
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
import com.binge.companion.sdk.problem
import com.binge.companion.sdk.serverUnreachableOf
import com.binge.companion.sdk.userMessageOf
import com.cooper.binge.companion.jellyfin.FakeItem
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.ServerEvent
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The whole LIBRARY contract over an in-process channel, against a fake Jellyfin server. */
@RunWith(RobolectricTestRunner::class)
class JellyfinLibraryServiceTest {
    private val jellyfin = FakeJellyfin()
    private val sessions: SessionStore = testSessionStore()
    private val connection =
        Connection(
            OkHttpClient.Builder().readTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build(),
            sessions,
            "Jellyfin Companion",
            "Test",
            "1.2.3",
            Dispatchers.IO,
        )
    private val messages =
        object : UserMessages {
            override fun signedOut() = "Sign in to Jellyfin"

            override fun unreachable() = "Can't reach Jellyfin"

            override fun locale() = "en-GB"
        }

    private val events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 8)
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
                    events = events,
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
            assertTrue(Capability.CAPABILITY_SERVER_UNREACHABLE in response.capabilitiesList)
            assertTrue(Capability.CAPABILITY_EPISODE_WATCH_STATE in response.capabilitiesList)
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

    private fun absentReason(): AvailabilityReason =
        runBlocking {
            stub
                .getAvailability(
                    GetAvailabilityRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_MOVIE, 1)).build(),
                ).availability.reason
        }

    @Test
    fun `a title that is not found says the server has titles with no TMDB id, when it does`() {
        assertEquals(AvailabilityReason.AVAILABILITY_REASON_SERVER_LACKS_IDS, absentReason())
    }

    @Test
    fun `a title that is not found says nothing more when every title the server holds has an id`() {
        jellyfin.items.removeAll { it.id == "untagged" }
        assertEquals(AvailabilityReason.AVAILABILITY_REASON_UNSPECIFIED, absentReason())
    }

    @Test
    fun `a title that is found carries no reason, even when the server has titles with no id`() =
        runBlocking {
            val held = stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()).availability
            assertEquals(AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY, held.state)
            assertEquals(AvailabilityReason.AVAILABILITY_REASON_UNSPECIFIED, held.reason)
        }

    @Test
    fun `an empty library says nothing more than not in library`() {
        jellyfin.items.clear()
        assertEquals(AvailabilityReason.AVAILABILITY_REASON_UNSPECIFIED, absentReason())
    }

    @Test
    fun `a count that cannot be read leaves the reason unspecified and does not fail the answer`() {
        jellyfin.failsTheMissingIdCount = true
        assertEquals(AvailabilityReason.AVAILABILITY_REASON_UNSPECIFIED, absentReason())
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

    private fun availabilityFailure() = statusOf { stub.getAvailability(GetAvailabilityRequest.newBuilder().setMedia(movie).build()) }

    @Test
    fun `a server error is the server not answering, and a refusal PERMISSION_DENIED`() {
        jellyfin.status["/Items"] = 500
        val unanswered = availabilityFailure()
        assertEquals(Status.Code.UNAVAILABLE, unanswered.status.code)
        assertEquals("truenas", serverUnreachableOf(unanswered)?.serverName)
        jellyfin.status["/Items"] = 403
        val refused = availabilityFailure()
        assertEquals(Status.Code.PERMISSION_DENIED, refused.status.code)
        assertNull(serverUnreachableOf(refused))
    }

    @Test
    fun `a server that refuses connections is UNAVAILABLE, naming the server, with a sentence for the user`() {
        jellyfin.close()
        val failure = availabilityFailure()
        assertEquals(Status.Code.UNAVAILABLE, failure.status.code)
        val unreachable = serverUnreachableOf(failure)
        assertEquals("truenas", unreachable?.serverName)
        assertEquals("Can't reach Jellyfin", userMessageOf(failure.trailers)?.message)
    }

    @Test
    fun `a server that does not answer in time is DEADLINE_EXCEEDED, naming the server`() {
        jellyfin.slow += "/Items"
        val failure = availabilityFailure()
        assertEquals(Status.Code.DEADLINE_EXCEEDED, failure.status.code)
        val unreachable = serverUnreachableOf(failure)
        assertEquals("truenas", unreachable?.serverName)
    }

    @Test
    fun `a server that answered with an error on the merits is not the server not answering`() {
        jellyfin.status["/Items"] = 404
        val notFound = availabilityFailure()
        assertNull(serverUnreachableOf(notFound))
        jellyfin.status["/Items"] = 401
        val refusedToken = availabilityFailure()
        assertEquals(Status.Code.UNAUTHENTICATED, refusedToken.status.code)
        assertNull(serverUnreachableOf(refusedToken))
    }

    @Test
    fun `no session stays UNAUTHENTICATED, never the server not answering`() {
        runBlocking { sessions.clear() }
        jellyfin.close()
        val failure = availabilityFailure()
        assertEquals(Status.Code.UNAUTHENTICATED, failure.status.code)
        assertNull(serverUnreachableOf(failure))
    }

    @Test
    fun `a server with no name is named by nothing`() {
        runBlocking { sessions.save(jellyfin.session().copy(serverName = "")) }
        jellyfin.close()
        assertNull(serverUnreachableOf(availabilityFailure())?.serverName)
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

    /** A season of [count] episodes of the series, two of them played: 115 most recently, 118 earlier. */
    private fun addLongSeason(count: Int = 120) {
        jellyfin.items +=
            (1..count).map { n ->
                FakeItem(
                    "long-$n",
                    "Episode $n",
                    "Episode",
                    seriesId = "series-1",
                    season = 2,
                    episode = n,
                    played = n == 115 || n == 118,
                    lastPlayed =
                        when (n) {
                            115 -> "2026-11-01T10:00:00Z"
                            118 -> "2026-10-01T10:00:00Z"
                            else -> null
                        },
                )
            }
    }

    @Test
    fun `a long series' watch state is the window, the most recently played first, even past the hundredth episode`() =
        runBlocking {
            addLongSeason()
            val state = stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(series).build()).state
            assertEquals(100, state.episodesCount)
            val order = state.episodesList.map { it.episode.seasonNumber to it.episode.episodeNumber }
            // The two played ones lead, latest first, though both are late in season order; the rest follow in season order.
            assertEquals(listOf(2 to 115, 2 to 118, 1 to 1, 1 to 2, 2 to 1), order.take(5))
            assertEquals(2 to 96, order.last())
            assertFalse(2 to 100 in order)
            // The series' own last_played reads every episode, not only the window.
            assertEquals(Instant.parse("2026-11-01T10:00:00Z").epochSecond, state.lastPlayed.seconds)
        }

    private fun listEpisodes(
        token: String = "",
        size: Int = 0,
    ) = ListEpisodeWatchStateRequest
        .newBuilder()
        .setMedia(series)
        .setPageToken(token)
        .setPageSize(size)
        .build()

    @Test
    fun `episode watch state pages through a long series in order, past the window`() =
        runBlocking {
            jellyfin.items +=
                (1..250).map { n ->
                    FakeItem("many-$n", "E$n", "Episode", seriesId = "series-1", season = 2, episode = n, played = true)
                } + FakeItem("special", "Special", "Episode", seriesId = "series-1", season = 0, episode = 1, positionTicks = 600_000_000L)
            val seen = mutableListOf<Pair<Int, Int>>()
            var token = ""
            var pages = 0
            do {
                val page = stub.listEpisodeWatchState(listEpisodes(token, size = 100))
                assertTrue(page.episodesCount <= 100)
                seen += page.episodesList.map { it.episode.seasonNumber to it.episode.episodeNumber }
                token = page.nextPageToken
                pages++
            } while (token.isNotEmpty())
            // The special first, the played ep 1 of season 1, then season 2; ep 2 of season 1 has no state.
            assertEquals(listOf(0 to 1, 1 to 1), seen.take(2))
            assertEquals(252, seen.size)
            assertEquals(seen.sortedWith(compareBy({ it.first }, { it.second })), seen)
            assertEquals(3, pages)
        }

    @Test
    fun `episode watch state with no page size sends the default and leaves out episodes with no state`() =
        runBlocking {
            val page = stub.listEpisodeWatchState(listEpisodes())
            assertEquals(listOf(1), page.episodesList.map { it.episode.episodeNumber })
            assertTrue(
                page.episodesList
                    .single()
                    .state.played,
            )
            assertEquals("", page.nextPageToken)
        }

    @Test
    fun `episode watch state rejects a movie, a bad token and a missing title, and needs a session`() {
        val movieRequest = ListEpisodeWatchStateRequest.newBuilder().setMedia(movie).build()
        assertEquals(Status.Code.INVALID_ARGUMENT, statusOf { stub.listEpisodeWatchState(movieRequest) }.status.code)
        assertEquals(Status.Code.INVALID_ARGUMENT, statusOf { stub.listEpisodeWatchState(listEpisodes("p1s0")) }.status.code)
        val unknown = ListEpisodeWatchStateRequest.newBuilder().setMedia(media(MediaType.MEDIA_TYPE_TV, 424242)).build()
        assertEquals(Status.Code.NOT_FOUND, statusOf { stub.listEpisodeWatchState(unknown) }.status.code)
        runBlocking { sessions.clear() }
        assertEquals(Status.Code.UNAUTHENTICATED, statusOf { stub.listEpisodeWatchState(listEpisodes()) }.status.code)
    }

    @Test
    fun `a series that fits the window keeps every episode`() =
        runBlocking {
            addLongSeason(count = 10)
            val state = stub.getWatchState(GetWatchStateRequest.newBuilder().setMedia(series).build()).state
            assertEquals(12, state.episodesCount)
        }

    @Test
    fun `a watch state stream pushes the same window`() =
        runBlocking {
            addLongSeason()
            val first =
                withTimeout(10_000) {
                    stub.observeWatchState(ObserveWatchStateRequest.newBuilder().setMedia(series).build()).first()
                }
            assertEquals(100, first.state.episodesCount)
            assertEquals(
                2 to 115,
                first.state.episodesList
                    .first()
                    .episode
                    .let { it.seasonNumber to it.episodeNumber },
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

    private fun sets(
        sourceId: String,
        vararg widths: Int,
        count: Int = 360,
    ) = """"$sourceId":{${widths.joinToString(
        ",",
    ) {
        """"$it":{"Width":$it,"Height":${it * 9 / 16},"TileWidth":10,"TileHeight":10,"ThumbnailCount":$count,"Interval":10000,"Bandwidth":1}"""
    }}}"""

    private fun withTrickplay(trickplay: String?) {
        val index = jellyfin.items.indexOfFirst { it.id == "movie-1" }
        jellyfin.items[index] = jellyfin.items[index].copy(trickplay = trickplay)
    }

    private fun playbackSource(playerPackage: String = "") =
        runBlocking {
            stub.getPlaybackSource(
                GetPlaybackSourceRequest
                    .newBuilder()
                    .setMedia(movie)
                    .setPlayerPackage(playerPackage)
                    .build(),
            )
        }

    @Test
    fun `a source carries the trickplay descriptor for the media source, with no credential in the template`() {
        withTrickplay("{${sets("source-1", 160, 320, 640)}}")

        val trickplay = playbackSource().trickplay

        assertEquals("${jellyfin.baseUrl}/Videos/source-1/Trickplay/320/{index}.jpg", trickplay.urlTemplate)
        assertEquals(320, trickplay.thumbnailWidth)
        assertEquals(180, trickplay.thumbnailHeight)
        assertEquals(10, trickplay.tileColumns)
        assertEquals(10, trickplay.tileRows)
        assertEquals(10_000L, trickplay.intervalMillis)
        assertEquals(360, trickplay.thumbnailCount)
        assertFalse(trickplay.urlTemplate.contains("token-1"))
        assertFalse(trickplay.urlTemplate.contains("ApiKey", ignoreCase = true))
        assertEquals(null, trickplay.problem())
    }

    @Test
    fun `a title with no trickplay sends none, and that is not an error`() {
        assertFalse(playbackSource().hasTrickplay())
    }

    @Test
    fun `the descriptor is made for the media source being played, not another version of the item`() {
        withTrickplay("{${sets("source-2", 320)},${sets("source-1", 160)}}")

        val trickplay = playbackSource().trickplay

        assertTrue(trickplay.urlTemplate.contains("/Videos/source-1/Trickplay/160/"))
        assertEquals(160, trickplay.thumbnailWidth)
    }

    @Test
    fun `sets held for another version only send nothing`() {
        withTrickplay("{${sets("source-2", 320)}}")

        assertFalse(playbackSource().hasTrickplay())
    }

    @Test
    fun `a descriptor the contract would drop is not sent`() {
        withTrickplay("{${sets("source-1", 320, count = 0)}}")

        assertFalse(playbackSource().hasTrickplay())
    }

    @Test
    fun `a server older than trickplay sends none and is not asked for it`() {
        runBlocking { sessions.save(jellyfin.session().copy(serverVersion = "10.8.13")) }
        withTrickplay("{${sets("source-1", 320)}}")

        assertFalse(playbackSource().hasTrickplay())
        assertTrue(jellyfin.requests.none { it.url.queryParameter("fields")?.contains("Trickplay") == true })
    }

    @Test
    fun `an installed player gets no trickplay, since it cannot send the headers the sheets need, and is not asked for it`() {
        withTrickplay("{${sets("source-1", 320)}}")

        val source = playbackSource(playerPackage = "org.videolan.vlc")

        assertFalse(source.hasTrickplay())
        assertTrue(source.source.url.contains("ApiKey=token-1"))
        assertTrue(jellyfin.requests.none { it.url.queryParameter("fields")?.contains("Trickplay") == true })
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
        for (token in listOf("x", "p", "p-1", "12", "p2", "p+2s0", "p02s0", "p2s")) {
            val resume = statusOf { stub.listContinueWatching(ListContinueWatchingRequest.newBuilder().setPageToken(token).build()) }
            assertEquals(token, Status.Code.INVALID_ARGUMENT, resume.status.code)
            val nextUp = statusOf { stub.listNextUp(ListNextUpRequest.newBuilder().setPageToken(token).build()) }
            assertEquals(token, Status.Code.INVALID_ARGUMENT, nextUp.status.code)
        }
    }

    @Test
    fun `a page_token replayed under a different page_size is INVALID_ARGUMENT`() =
        runBlocking {
            jellyfin.items += (2..3).map { FakeItem("movie-$it", "Movie $it", "Movie", tmdbId = 2000 + it, positionTicks = 10_000_000L) }
            val first = stub.listContinueWatching(ListContinueWatchingRequest.newBuilder().setPageSize(2).build())
            val resume =
                statusOf {
                    stub.listContinueWatching(
                        ListContinueWatchingRequest
                            .newBuilder()
                            .setPageSize(3)
                            .setPageToken(first.nextPageToken)
                            .build(),
                    )
                }
            assertEquals(Status.Code.INVALID_ARGUMENT, resume.status.code)
            val nextUp =
                statusOf {
                    stub.listNextUp(
                        ListNextUpRequest
                            .newBuilder()
                            .setPageSize(3)
                            .setPageToken(first.nextPageToken)
                            .build(),
                    )
                }
            assertEquals(Status.Code.INVALID_ARGUMENT, nextUp.status.code)
        }

    @Test
    fun `a negative page_size pages on, because the token it issued is accepted back`() =
        runBlocking {
            jellyfin.items +=
                (2..22).map { FakeItem("movie-$it", "Movie $it", "Movie", tmdbId = 2000 + it, positionTicks = 10_000_000L) }
            val first = stub.listContinueWatching(ListContinueWatchingRequest.newBuilder().setPageSize(-1).build())
            assertEquals("p20s-1", first.nextPageToken)
            val second =
                stub.listContinueWatching(
                    ListContinueWatchingRequest
                        .newBuilder()
                        .setPageSize(-1)
                        .setPageToken(first.nextPageToken)
                        .build(),
                )
            assertEquals(2, second.entriesCount)
            assertEquals("", second.nextPageToken)
        }

    @Test
    fun `next up pages on under a negative page_size`() =
        runBlocking {
            jellyfin.items +=
                (1..21).map {
                    FakeItem("ep-$it", "Episode $it", "Episode", seriesId = "series-$it", season = 1, episode = 1)
                }
            val first = stub.listNextUp(ListNextUpRequest.newBuilder().setPageSize(-1).build())
            assertEquals("p20s-1", first.nextPageToken)
            val second =
                stub.listNextUp(
                    ListNextUpRequest
                        .newBuilder()
                        .setPageSize(-1)
                        .setPageToken(first.nextPageToken)
                        .build(),
                )
            assertEquals("", second.nextPageToken)
        }

    @Test
    fun `continue watching pages through the whole list, and the last page carries no token`() =
        runBlocking {
            jellyfin.items += (2..3).map { FakeItem("movie-$it", "Movie $it", "Movie", tmdbId = 2000 + it, positionTicks = 10_000_000L) }
            val first = stub.listContinueWatching(ListContinueWatchingRequest.newBuilder().setPageSize(2).build())
            assertEquals(2, first.entriesCount)
            assertEquals("p2s2", first.nextPageToken)
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

    @Test
    fun `a watch state stream pushes again when the server says user data changed`() =
        runBlocking {
            val states = LinkedBlockingQueue<Long>()
            val job =
                launch(Dispatchers.IO) {
                    stub.observeWatchState(ObserveWatchStateRequest.newBuilder().setMedia(movie).build()).collect {
                        states.put(it.state.positionMillis)
                    }
                }
            assertEquals(526_000L, states.poll(10, TimeUnit.SECONDS))
            // The stream subscribes to the events after its first read: wait for it, or the event is dropped.
            events.subscriptionCount.first { it > 0 }
            val index = jellyfin.items.indexOfFirst { it.id == "movie-1" }
            jellyfin.items[index] = jellyfin.items[index].copy(positionTicks = 9_000_000_000L)
            events.emit(ServerEvent.USER_DATA_CHANGED)
            assertEquals(900_000L, states.poll(10, TimeUnit.SECONDS))
            job.cancel()
        }
}

private const val CALL_TIMEOUT_SECONDS = 2L
