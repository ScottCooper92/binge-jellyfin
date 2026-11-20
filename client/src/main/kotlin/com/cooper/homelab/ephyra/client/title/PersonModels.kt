package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure

/** The person's facts under their name. A fact the server did not give is null and not drawn. */
data class PersonFacts(
    /** ISO 8601 as the server sends it; the screen words it in the user's locale. */
    val born: String?,
    val died: String?,
    val birthPlace: String?,
)

/** Where the person's titles on this server stand. They page in as the user scrolls. */
sealed interface CreditsState {
    data object Loading : CreditsState

    /** [hasMore] is whether another page can be asked for. */
    data class Ready(
        val hasMore: Boolean,
    ) : CreditsState

    /** A page could not be read; the titles already shown stay, and a retry asks for the next page again. */
    data class Failed(
        val failure: ServerFailure,
    ) : CreditsState
}

/** Everything the person page shows: who they are, and the movies and series on this server they appear in. */
data class PersonPageUi(
    val id: String,
    val name: String,
    val photo: TitleImage?,
    val facts: PersonFacts,
    val biography: String?,
    val credits: List<TitleCard>,
    val creditsState: CreditsState,
)

/** What the person page shows. */
sealed interface PersonUiState {
    data object Loading : PersonUiState

    data class Ready(
        val person: PersonPageUi,
    ) : PersonUiState

    /** The person could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : PersonUiState
}

/** One part of a collection, as the list shows it. */
data class CollectionPart(
    val card: TitleCard,
    val runtimeMinutes: Int?,
)

/** Everything the collection page shows: its parts in release order, with how many are watched. */
data class CollectionPageUi(
    val id: String,
    val name: String,
    val overview: String?,
    val backdrop: TitleImage?,
    val poster: TitleImage?,
    val parts: List<CollectionPart>,
    val watchedCount: Int,
)

/** What the collection page shows. */
sealed interface CollectionUiState {
    data object Loading : CollectionUiState

    data class Ready(
        val collection: CollectionPageUi,
    ) : CollectionUiState

    /** The collection could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : CollectionUiState
}
