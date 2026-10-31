package com.cooper.binge.companion.jellyfin.service

import android.util.Log
import com.binge.companion.contracts.library.v1.Availability
import com.binge.companion.contracts.library.v1.AvailabilityState
import com.binge.companion.contracts.library.v1.EpisodeRef
import com.binge.companion.contracts.library.v1.WatchState
import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import com.cooper.binge.companion.jellyfin.server.IndexKey
import com.cooper.binge.companion.jellyfin.server.IndexType
import com.cooper.binge.companion.jellyfin.server.JellyfinItem
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusException
import java.time.Instant
import java.time.format.DateTimeParseException

/** Jellyfin counts time in ticks of 100 ns; the contract in milliseconds. */
internal const val TICKS_PER_MILLI = 10_000L

private const val TAG = "JellyfinContract"

internal fun notInLibrary(): Availability = Availability.newBuilder().setState(AvailabilityState.AVAILABILITY_STATE_NOT_IN_LIBRARY).build()

internal fun notFound(): StatusException = StatusException(Status.NOT_FOUND.withDescription("not on this Jellyfin server"))

internal fun invalid(description: String): StatusException = StatusException(Status.INVALID_ARGUMENT.withDescription(description))

/** The index key for [this]; INVALID_ARGUMENT for an id no title can have. */
internal fun MediaId.toKey(): IndexKey {
    if (tmdbId <= 0) throw invalid("tmdb_id must be positive")
    return when (mediaType) {
        MediaType.MEDIA_TYPE_MOVIE -> IndexKey(IndexType.MOVIE, tmdbId)
        MediaType.MEDIA_TYPE_TV -> IndexKey(IndexType.SERIES, tmdbId)
        else -> throw invalid("media_type is unspecified")
    }
}

internal fun IndexKey.toMediaId(): MediaId =
    MediaId
        .newBuilder()
        .setMediaType(if (type == IndexType.MOVIE) MediaType.MEDIA_TYPE_MOVIE else MediaType.MEDIA_TYPE_TV)
        .setTmdbId(tmdbId)
        .build()

internal fun episodeRef(
    season: Int,
    episode: Int,
): EpisodeRef =
    EpisodeRef
        .newBuilder()
        .setSeasonNumber(season)
        .setEpisodeNumber(episode)
        .build()

/** The episode's position in its series, or null for an item that is not numbered as one. */
internal fun JellyfinItem.episodeRefOrNull(): EpisodeRef? {
    val season = seasonNumber ?: return null
    val number = episodeNumber ?: return null
    return episodeRef(season, number)
}

internal fun JellyfinItem.toWatchState(): WatchState {
    val builder =
        WatchState
            .newBuilder()
            .setPlayed(userData?.played == true)
            .setPositionMillis((userData?.positionTicks ?: 0L) / TICKS_PER_MILLI)
            .setRuntimeMillis((runTimeTicks ?: 0L) / TICKS_PER_MILLI)
    userData?.lastPlayedDate?.toTimestamp()?.let { builder.setLastPlayed(it) }
    return builder.build()
}

/** Jellyfin's ISO-8601 instant, or null for one it wrote some other way. */
internal fun String.toTimestamp(): Timestamp? =
    try {
        Instant.parse(this).toTimestamp()
    } catch (e: DateTimeParseException) {
        Log.w(TAG, "Unreadable date from the server", e)
        null
    }

internal fun Instant.toTimestamp(): Timestamp =
    Timestamp
        .newBuilder()
        .setSeconds(epochSecond)
        .setNanos(nano)
        .build()
