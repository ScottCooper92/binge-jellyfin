package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.Availability
import com.binge.companion.contracts.library.v1.AvailabilityState
import com.binge.companion.contracts.library.v1.EpisodeRef
import com.binge.companion.contracts.library.v1.EpisodeWatchState
import com.binge.companion.contracts.library.v1.LibraryEntry
import com.binge.companion.contracts.library.v1.SeasonAvailability
import com.binge.companion.contracts.library.v1.WatchState
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.binge.companion.sdk.withUserMessage
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinItem
import com.cooper.binge.companion.jellyfin.server.LibraryIndex
import com.cooper.binge.companion.jellyfin.server.episodes
import com.cooper.binge.companion.jellyfin.server.item
import io.grpc.Status

/** The signed-in user's library, read in the contract's terms: availability, watch state and list entries. */
class LibraryReads(
    private val connection: Connection,
    private val index: LibraryIndex,
    private val messages: UserMessages,
) {
    /** The session and a client for it; UNAUTHENTICATED, with a sentence for the user, when signed out. */
    suspend fun signedIn(): Pair<JellyfinSession, JellyfinClient> =
        connection.signedIn()
            ?: throw Status.UNAUTHENTICATED
                .withDescription("not signed in")
                .withUserMessage(REASON_SIGNED_OUT, messages.signedOut(), messages.locale())

    suspend fun availability(media: MediaId): Availability {
        val (session, client) = signedIn()
        val item = index.find(client, session.userId, media.toKey()) ?: return notInLibrary()
        val builder =
            Availability
                .newBuilder()
                .setState(AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY)
                .setItemId(item.id)
        if (media.mediaType == MediaType.MEDIA_TYPE_TV) {
            client
                .episodes(session.userId, item.id)
                .mapNotNull { it.episodeRefOrNull() }
                .groupBy { it.seasonNumber }
                .toSortedMap()
                .forEach { (season, refs) ->
                    builder.addSeasons(
                        SeasonAvailability
                            .newBuilder()
                            .setSeasonNumber(season)
                            .setState(AvailabilityState.AVAILABILITY_STATE_IN_LIBRARY)
                            .addAllEpisodeNumbers(refs.map(EpisodeRef::getEpisodeNumber).distinct().sorted()),
                    )
                }
        }
        return builder.build()
    }

    /** A movie's state, or a series' with each episode's; NOT_FOUND for a title the server does not hold. */
    suspend fun watchState(media: MediaId): WatchState {
        val (session, client) = signedIn()
        val item = index.find(client, session.userId, media.toKey()) ?: throw notFound()
        val fresh = client.item(session.userId, item.id) ?: item
        if (media.mediaType != MediaType.MEDIA_TYPE_TV) return fresh.toWatchState()
        val episodes = client.episodes(session.userId, item.id).mapNotNull { episode -> episode.episodeRefOrNull()?.let { it to episode } }
        val builder = fresh.toWatchState().toBuilder()
        episodes.forEach { (ref, episode) ->
            builder.addEpisodes(EpisodeWatchState.newBuilder().setEpisode(ref).setState(episode.toWatchState()))
        }
        episodes
            .mapNotNull { (_, episode) -> episode.userData?.lastPlayedDate?.toTimestamp() }
            .maxByOrNull { it.seconds }
            ?.let { builder.setLastPlayed(it) }
        return builder.build()
    }

    /** The movie, or the series' episode, as a Jellyfin item; NOT_FOUND when the server does not hold it. */
    suspend fun playableItem(
        session: JellyfinSession,
        client: JellyfinClient,
        media: MediaId,
        episode: EpisodeRef?,
    ): JellyfinItem {
        val title = index.find(client, session.userId, media.toKey()) ?: throw notFound()
        if (episode == null) return title
        return client
            .episodes(session.userId, title.id)
            .firstOrNull { it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber }
            ?: throw notFound()
    }

    /** [items] as list entries, named by TMDB id; an item the index cannot name is left out. */
    suspend fun entries(
        session: JellyfinSession,
        client: JellyfinClient,
        items: List<JellyfinItem>,
    ): List<LibraryEntry> =
        items.mapNotNull { item ->
            val episode = if (item.type == EPISODE) item.episodeRefOrNull() else null
            val ownerId = if (item.type == EPISODE) item.seriesId else item.id
            val key = ownerId?.let { index.keyOf(client, session.userId, it) } ?: return@mapNotNull null
            LibraryEntry
                .newBuilder()
                .setMedia(key.toMediaId())
                .setState(item.toWatchState())
                .setTitle(item.name)
                .setArtworkUrl(client.url("Items/$ownerId/Images/Primary", mapOf("maxHeight" to ARTWORK_HEIGHT)).toString())
                .apply { episode?.let(::setEpisode) }
                .build()
        }

    private companion object {
        const val EPISODE = "Episode"
        const val ARTWORK_HEIGHT = "600"
    }
}
