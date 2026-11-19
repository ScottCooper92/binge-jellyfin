package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.LibraryView

/** One library on the Libraries tab. [imageTag] is the tag of its own picture, when it has one. */
data class LibraryTile(
    val id: String,
    val name: String,
    val kind: LibraryKind,
    val imageTag: String?,
)

/** What the Libraries tab shows. */
sealed interface LibrariesUiState {
    /** The libraries have not been read yet. */
    data object Loading : LibrariesUiState

    /** At least one movie or series library. */
    data class Ready(
        val tiles: List<LibraryTile>,
    ) : LibrariesUiState

    /** The user has libraries, but none this app lists (only movies and series are). */
    data object Empty : LibrariesUiState

    data class Error(
        val failure: ServerFailure,
    ) : LibrariesUiState
}

/** Whether the Libraries tab lists a library of [this] kind: movies and series, for now. */
fun LibraryKind.listed(): Boolean = this == LibraryKind.Movies || this == LibraryKind.TvShows

/** The libraries the tab lists, in the order the server gave them, as tiles. */
fun List<LibraryView>.tiles(): List<LibraryTile> =
    filter { it.kind.listed() }.map { LibraryTile(it.id, it.name, it.kind, it.primaryImageTag) }
