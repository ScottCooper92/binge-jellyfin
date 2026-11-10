package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.EpisodeWatchState

/** Entries in a `ListEpisodeWatchState` page when the host asks for none. */
internal const val EPISODE_PAGE_DEFAULT = 100

/** The most entries `ListEpisodeWatchState` sends in a page, whatever is asked. */
internal const val EPISODE_PAGE_MAX = 200

private val EPISODE_TOKEN = Regex("k(0|-?[1-9][0-9]*)e(0|-?[1-9][0-9]*)s(0|-?[1-9][0-9]*)")

private val SEASON_THEN_EPISODE: Comparator<EpisodeWatchState> =
    compareBy<EpisodeWatchState> { it.episode.seasonNumber }.thenBy { it.episode.episodeNumber }

/** One page of `ListEpisodeWatchState`: the entries and the token for the next page, empty after the last. */
internal data class EpisodeWatchPage(
    val episodes: List<EpisodeWatchState>,
    val nextPageToken: String,
)

/** Whether the user has some state for the episode: played, or a resume point. */
internal fun EpisodeWatchState.isWatched(): Boolean = state.played || state.positionMillis > 0

/**
 * The page of [this] that [token] asks for, under the [requestedSize] the host sent.
 *
 * Only episodes with state are listed, ascending by season then episode, so a token stays stable while the user watches. The token
 * names the last episode sent, not an offset: an earlier episode gaining state between pages moves nothing. It is valid only with
 * the page size it was issued under; anything else is INVALID_ARGUMENT.
 */
internal fun List<EpisodeWatchState>.episodePage(
    token: String,
    requestedSize: Int,
): EpisodeWatchPage {
    val after = if (token.isEmpty()) null else afterOf(token, requestedSize)
    val size = if (requestedSize <= 0) EPISODE_PAGE_DEFAULT else requestedSize.coerceAtMost(EPISODE_PAGE_MAX)
    val ordered =
        filter { it.isWatched() }
            .sortedWith(SEASON_THEN_EPISODE)
            .filter { after == null || SEASON_THEN_EPISODE.compare(it, after) > 0 }
    val page = ordered.take(size)
    val last = page.lastOrNull()
    val next =
        if (last != null &&
            ordered.size > page.size
        ) {
            "k${last.episode.seasonNumber}e${last.episode.episodeNumber}s$requestedSize"
        } else {
            ""
        }
    return EpisodeWatchPage(page, next)
}

private fun afterOf(
    token: String,
    requestedSize: Int,
): EpisodeWatchState {
    val (season, episode, size) = EPISODE_TOKEN.matchEntire(token)?.destructured ?: throw invalid("unknown page_token")
    val seasonNumber = season.toIntOrNull()
    val episodeNumber = episode.toIntOrNull()
    if (size != requestedSize.toString() || seasonNumber == null || episodeNumber == null) throw invalid("unknown page_token")
    return EpisodeWatchState
        .newBuilder()
        .setEpisode(episodeRef(seasonNumber, episodeNumber))
        .build()
}
