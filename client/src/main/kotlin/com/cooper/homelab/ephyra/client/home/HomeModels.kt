package com.cooper.homelab.ephyra.client.home

import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind

/** Which row of Home: what the user is watching, what is next, or what is new in one library. */
sealed interface HomeRowKey {
    data object ContinueWatching : HomeRowKey

    data object NextUp : HomeRowKey

    /** The newest titles in one of the user's libraries. */
    data class Latest(
        val libraryId: String,
        val libraryName: String,
    ) : HomeRowKey
}

/** A picture to show, named by item, kind and tag. The screen turns it into a URL at the size it draws it. */
data class HomeImage(
    val itemId: String,
    val kind: ImageKind,
    val tag: String,
)

/** What a card says under its title. The screen words it, so it is in the user's language. */
sealed interface HomeCaption {
    data object None : HomeCaption

    data class Year(
        val year: Int,
    ) : HomeCaption

    /** An episode: its place in the series, and its own name. */
    data class Episode(
        val season: Int?,
        val episode: Int?,
        val name: String,
    ) : HomeCaption
}

/** One card of a row. For an episode the title is the series', so the card reads as the show the user is watching. */
data class HomeCard(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val caption: HomeCaption,
    val poster: HomeImage?,
    val backdrop: HomeImage?,
    /** How far through it the user is, 0 to 1; null when they have not started it or have finished it. */
    val progress: Float?,
    val rating: Double?,
    /** Whether tapping the card resumes it, as Continue watching's do, rather than opening its page. */
    val resumes: Boolean,
)

/** A row that has something to show. A row with nothing is never built. */
data class HomeRow(
    val key: HomeRowKey,
    val cards: List<HomeCard>,
)

/** The card that fronts Home, with the picture it is shown on. */
data class HomeHero(
    val card: HomeCard,
    val backdrop: HomeImage,
)

/** What Home shows. */
sealed interface HomeUiState {
    /** No row has answered yet. */
    data object Loading : HomeUiState

    /** At least one row has something to show. [pending] rows are still loading and fill in below. */
    data class Ready(
        val hero: HomeHero?,
        val rows: List<HomeRow>,
        val pending: Int = 0,
    ) : HomeUiState

    /** Every row answered and none has anything to show. */
    data object Empty : HomeUiState

    /** Every row failed. */
    data class Error(
        val cause: Throwable,
    ) : HomeUiState
}
