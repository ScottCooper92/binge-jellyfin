package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.EpisodeWatchState

/** The most episodes `WatchState.episodes` holds in `GetWatchState` and `ObserveWatchState`; a host reads no more. */
internal const val EPISODE_WINDOW = 100

private val MOST_RECENT_FIRST: Comparator<EpisodeWatchState> =
    compareByDescending<EpisodeWatchState> { it.state.hasLastPlayed() }
        .thenByDescending { it.state.lastPlayed.seconds }
        .thenByDescending { it.state.lastPlayed.nanos }
        .thenBy { it.episode.seasonNumber }
        .thenBy { it.episode.episodeNumber }

/**
 * The window the contract gives a long series: at most [EPISODE_WINDOW] episodes, most recently played first (`last_played`
 * descending), then those with none in season and episode order. A series longer than the window would otherwise show a host only its
 * early seasons, and every push would grow with the series.
 */
internal fun List<EpisodeWatchState>.episodeWindow(): List<EpisodeWatchState> = sortedWith(MOST_RECENT_FIRST).take(EPISODE_WINDOW)
