package com.cooper.binge.companion.jellyfin.service

import android.util.Log
import com.binge.companion.contracts.library.v1.Capability
import com.binge.companion.contracts.library.v1.GetAvailabilityRequest
import com.binge.companion.contracts.library.v1.GetAvailabilityResponse
import com.binge.companion.contracts.library.v1.GetPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.GetPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.GetWatchStateRequest
import com.binge.companion.contracts.library.v1.GetWatchStateResponse
import com.binge.companion.contracts.library.v1.HandshakeRequest
import com.binge.companion.contracts.library.v1.HandshakeResponse
import com.binge.companion.contracts.library.v1.LibraryServiceGrpcKt
import com.binge.companion.contracts.library.v1.ListContinueWatchingRequest
import com.binge.companion.contracts.library.v1.ListContinueWatchingResponse
import com.binge.companion.contracts.library.v1.ListNextUpRequest
import com.binge.companion.contracts.library.v1.ListNextUpResponse
import com.binge.companion.contracts.library.v1.ObserveAvailabilityRequest
import com.binge.companion.contracts.library.v1.ObserveAvailabilityResponse
import com.binge.companion.contracts.library.v1.ObserveWatchStateRequest
import com.binge.companion.contracts.library.v1.ObserveWatchStateResponse
import com.binge.companion.contracts.library.v1.ReportPlaybackRequest
import com.binge.companion.contracts.library.v1.ReportPlaybackResponse
import com.binge.companion.contracts.library.v1.SetPlayedRequest
import com.binge.companion.contracts.library.v1.SetPlayedResponse
import com.binge.companion.sdk.requireDeclared
import com.binge.companion.sdk.withUserMessage
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.server.nextUp
import com.cooper.binge.companion.jellyfin.server.resumeItems
import com.cooper.binge.companion.jellyfin.server.setPlayed
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The sentences the host may show for a failure, in the device's language. */
interface UserMessages {
    fun signedOut(): String

    fun unreachable(): String

    fun locale(): String
}

/**
 * LIBRARY v1 over the signed-in Jellyfin user: whether the server holds a title, the user's watch
 * state both ways, continue watching and next up, and a playback source for the host's player with
 * the plays reported back. This class is the gRPC surface and the error model; [LibraryReads] and
 * [PlaybackSources] do the work.
 */
class JellyfinLibraryService(
    private val reads: LibraryReads,
    private val playback: PlaybackSources,
    private val versionName: String,
    private val messages: UserMessages,
) : LibraryServiceGrpcKt.LibraryServiceCoroutineImplBase() {
    override suspend fun handshake(request: HandshakeRequest): HandshakeResponse =
        guard {
            reads.signedIn()
            HandshakeResponse
                .newBuilder()
                .addAllCapabilities(CAPABILITIES.sortedBy(Capability::getNumber))
                .setProviderName(PROVIDER_NAME)
                .setCompanionVersionName(versionName)
                .build()
        }

    override suspend fun getAvailability(request: GetAvailabilityRequest): GetAvailabilityResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_AVAILABILITY)
            GetAvailabilityResponse.newBuilder().setAvailability(reads.availability(request.media)).build()
        }

    override fun observeAvailability(request: ObserveAvailabilityRequest): Flow<ObserveAvailabilityResponse> =
        poll { reads.availability(request.media) }.map { ObserveAvailabilityResponse.newBuilder().setAvailability(it).build() }

    override suspend fun getWatchState(request: GetWatchStateRequest): GetWatchStateResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_WATCH_STATE)
            GetWatchStateResponse.newBuilder().setState(reads.watchState(request.media)).build()
        }

    override fun observeWatchState(request: ObserveWatchStateRequest): Flow<ObserveWatchStateResponse> =
        poll { reads.watchState(request.media) }.map { ObserveWatchStateResponse.newBuilder().setState(it).build() }

    override suspend fun setPlayed(request: SetPlayedRequest): SetPlayedResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_WATCH_STATE_WRITE)
            val (session, client) = reads.signedIn()
            val target = reads.playableItem(session, client, request.media, if (request.hasEpisode()) request.episode else null)
            client.setPlayed(session.userId, target.id, request.played)
            SetPlayedResponse.getDefaultInstance()
        }

    override suspend fun listContinueWatching(request: ListContinueWatchingRequest): ListContinueWatchingResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_CONTINUE_WATCHING)
            val start = startOf(request.pageToken)
            val (session, client) = reads.signedIn()
            val page = client.resumeItems(session.userId, start, pageSize(request.pageSize))
            ListContinueWatchingResponse
                .newBuilder()
                .addAllEntries(reads.entries(session, client, page.items))
                .setNextPageToken(nextTokenAfter(start, page))
                .build()
        }

    override suspend fun listNextUp(request: ListNextUpRequest): ListNextUpResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_CONTINUE_WATCHING)
            val start = startOf(request.pageToken)
            val (session, client) = reads.signedIn()
            val page = client.nextUp(session.userId, start, pageSize(request.pageSize))
            ListNextUpResponse
                .newBuilder()
                .addAllEntries(reads.entries(session, client, page.items))
                .setNextPageToken(nextTokenAfter(start, page))
                .build()
        }

    override suspend fun getPlaybackSource(request: GetPlaybackSourceRequest): GetPlaybackSourceResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_PLAYBACK_SOURCE)
            val (session, client) = reads.signedIn()
            val episode = if (request.hasEpisode()) request.episode else null
            requireEpisodeFits(request.media, episode)
            playback.mint(session, client, reads.playableItem(session, client, request.media, episode), request)
        }

    override suspend fun reportPlayback(request: ReportPlaybackRequest): ReportPlaybackResponse =
        guard {
            CAPABILITIES.requireDeclared(Capability.CAPABILITY_PLAYBACK_PROGRESS)
            requireEpisodeFits(request.media, if (request.hasEpisode()) request.episode else null)
            val (_, client) = reads.signedIn()
            playback.report(client, request)
            ReportPlaybackResponse.getDefaultInstance()
        }

    /** Pushes the state now, then whenever a poll finds it changed: Jellyfin's websocket is a later improvement. */
    private fun <T> poll(read: suspend () -> T): Flow<T> =
        flow {
            while (true) {
                emit(guard { read() })
                delay(OBSERVE_POLL_MILLIS)
            }
        }.distinctUntilChanged()

    /** The contract's error model: a broken session is UNAUTHENTICATED, a missing item NOT_FOUND, a network failure UNAVAILABLE. */
    private suspend fun <T> guard(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: StatusException) {
            throw e
        } catch (e: JellyfinHttpException) {
            Log.w(TAG, "Jellyfin answered ${e.code}", e)
            throw when (e.code) {
                HTTP_UNAUTHORIZED ->
                    Status.UNAUTHENTICATED
                        .withDescription("Jellyfin refused the token")
                        .withCause(e)
                        .withUserMessage(REASON_SIGNED_OUT, messages.signedOut(), messages.locale())
                HTTP_FORBIDDEN -> StatusException(Status.PERMISSION_DENIED.withDescription("Jellyfin answered 403").withCause(e))
                HTTP_NOT_FOUND -> StatusException(Status.NOT_FOUND.withDescription("not on this Jellyfin server").withCause(e))
                else -> StatusException(Status.UNAVAILABLE.withDescription("Jellyfin answered ${e.code}").withCause(e))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Jellyfin unreachable", e)
            throw Status.UNAVAILABLE
                .withDescription("Jellyfin unreachable")
                .withCause(e)
                .withUserMessage(REASON_UNREACHABLE, messages.unreachable(), messages.locale())
        }

    private companion object {
        const val TAG = "JellyfinLibrary"
        const val PROVIDER_NAME = "Jellyfin"
        const val REASON_UNREACHABLE = "UNREACHABLE"
        const val OBSERVE_POLL_MILLIS = 30_000L
        const val DEFAULT_PAGE = 20
        const val MAX_PAGE = 50
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404

        val CAPABILITIES =
            setOf(
                Capability.CAPABILITY_AVAILABILITY,
                Capability.CAPABILITY_WATCH_STATE,
                Capability.CAPABILITY_WATCH_STATE_WRITE,
                Capability.CAPABILITY_CONTINUE_WATCHING,
                Capability.CAPABILITY_PLAYBACK_SOURCE,
                Capability.CAPABILITY_PLAYBACK_PROGRESS,
            )

        fun pageSize(requested: Int): Int = if (requested <= 0) DEFAULT_PAGE else requested.coerceAtMost(MAX_PAGE)
    }
}
