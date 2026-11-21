package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.TitleLink

/** The headline stats under a series' hero. A stat the server did not give is null and not drawn. */
data class SeriesStats(
    val rating: Double?,
    val year: Int?,
    /** How many seasons it has, not counting specials. */
    val seasonCount: Int,
    val certification: String?,
    /** The episodes the seasons hold, not counting specials, when the server counts them. */
    val episodeCount: Int? = null,
) {
    val isEmpty: Boolean get() = rating == null && year == null && seasonCount == 0 && certification == null && episodeCount == null
}

/** What the series' one wide button does. */
sealed interface SeriesPlay {
    /** The episode the button plays or resumes, and where it sits, with its own [name]. [kind] says which. */
    data class Episode(
        val kind: PlayKind,
        val episodeId: String,
        val season: Int?,
        val episode: Int?,
        val minutesLeft: Int?,
        val name: String? = null,
    ) : SeriesPlay

    /** Every episode is watched, so there is nothing to continue with. The page offers [first], the first episode, to watch again. */
    data class AllWatched(
        val first: Episode,
    ) : SeriesPlay

    /** The series has no episode on the server. */
    data object NoEpisodes : SeriesPlay
}

/** One season, as the picker and the list's header show it. Season 0 is specials: the page decides where they sit. */
data class SeasonUi(
    val id: String,
    val number: Int,
    val name: String,
    val episodeCount: Int?,
    val unwatched: Int?,
    val watched: Boolean,
    val poster: TitleImage?,
) {
    val isSpecials: Boolean get() = number == 0
}

/** One episode of a season, as its row shows it. */
data class EpisodeUi(
    val id: String,
    val season: Int?,
    val number: Int?,
    val title: String,
    val overview: String?,
    /** The episode's own still, or its series' backdrop when it has none. */
    val still: TitleImage?,
    val runtimeMinutes: Int?,
    val watched: Boolean,
    /** How far through it the user is, 0 to 1; null when not started or finished. */
    val progress: Float?,
)

/** A season's episodes, which load when the season is chosen and fail on their own. */
sealed interface EpisodesState {
    data object Loading : EpisodesState

    data class Ready(
        val episodes: List<EpisodeUi>,
    ) : EpisodesState

    data class Failed(
        val failure: ServerFailure,
    ) : EpisodesState
}

/** Everything the series page shows. [seasons] are in the server's order, specials where the server puts them. */
data class SeriesUi(
    val id: String,
    val title: String,
    val tagline: String?,
    val genres: List<String>,
    val backdrop: TitleImage?,
    /** The series' poster, drawn beside the title at the expanded width. */
    val poster: TitleImage?,
    val stats: SeriesStats,
    /** What the one wide button does, or null while that is not known yet (still loading, or the read failed). */
    val play: SeriesPlay?,
    val watched: Boolean,
    val favourite: Boolean,
    val overview: String?,
    val facts: TitleFacts,
    val cast: List<PersonUi>,
    val crew: List<PersonUi>,
    val seasons: List<SeasonUi>,
    val selectedSeasonId: String?,
    val episodes: EpisodesState,
    val similar: List<TitleCard>,
    val criticsPercent: Int? = null,
    val trailerUrl: String? = null,
    val shareUrl: String? = null,
    val tags: List<String> = emptyList(),
    val links: List<TitleLink> = emptyList(),
)

/** What the series page shows. */
sealed interface SeriesUiState {
    data object Loading : SeriesUiState

    data class Ready(
        val series: SeriesUi,
    ) : SeriesUiState

    /** The series could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : SeriesUiState
}
