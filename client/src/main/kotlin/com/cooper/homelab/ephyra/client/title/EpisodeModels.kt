package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure

/** The headline stats under an episode's hero. A stat the server did not give is null and not drawn. */
data class EpisodeStats(
    val rating: Double?,
    val runtimeMinutes: Int?,
    val certification: String?,
) {
    val isEmpty: Boolean get() = rating == null && runtimeMinutes == null && certification == null
}

/** Everything the episode page shows: the movie page's shape over an episode, with the series and season it belongs to. */
data class EpisodePageUi(
    val id: String,
    val title: String,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    /** The episode's own still, else its series' backdrop. */
    val still: TitleImage?,
    val stats: EpisodeStats,
    val play: PlayAction,
    val watched: Boolean,
    val favourite: Boolean,
    val overview: String?,
    /** The air date as the server sends it (`yyyy-MM-dd…`); the screen words it in the user's locale. */
    val airDate: String?,
    val versions: List<VersionUi>,
    val guestCast: List<PersonUi>,
    val crew: List<PersonUi>,
    /** The other episodes of the season, which load after the page and fail on their own. */
    val season: EpisodesState,
)

/** What the episode page shows. */
sealed interface EpisodeUiState {
    data object Loading : EpisodeUiState

    data class Ready(
        val episode: EpisodePageUi,
    ) : EpisodeUiState

    /** The episode could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : EpisodeUiState
}

/** Everything the season page shows. */
data class SeasonPageUi(
    val id: String,
    val name: String,
    val seasonNumber: Int,
    val seriesId: String?,
    val seriesName: String?,
    val poster: TitleImage?,
    val overview: String?,
    /** The episode the button plays or resumes, or why there is none. */
    val play: SeriesPlay,
    val watched: Boolean,
    val episodes: EpisodesState,
) {
    val isSpecials: Boolean get() = seasonNumber == 0
}

/** What the season page shows. */
sealed interface SeasonUiState {
    data object Loading : SeasonUiState

    data class Ready(
        val season: SeasonPageUi,
    ) : SeasonUiState

    /** The season could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : SeasonUiState
}
