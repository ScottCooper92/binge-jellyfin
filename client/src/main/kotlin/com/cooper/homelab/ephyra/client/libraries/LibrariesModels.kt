package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.LibraryView

/**
 * One library on the Libraries tab. [imageTag] is the tag of the picture it is drawn with, when it has one, and [imageKind] says which
 * picture that is: a Thumb or a Backdrop has no name in it, so the tile draws the name over it; a Primary has the name drawn in already.
 */
data class LibraryTile(
    val id: String,
    val name: String,
    val kind: LibraryKind,
    val imageTag: String?,
    val imageKind: ImageKind = ImageKind.Primary,
) {
    /** Whether the tile writes the name over its picture: not when the picture is a Primary, which carries the name itself. */
    val showsName: Boolean get() = imageTag == null || imageKind != ImageKind.Primary
}

/** What the Libraries tab shows. */
sealed interface LibrariesUiState {
    /** The libraries have not been read yet. */
    data object Loading : LibrariesUiState

    /** At least one library this app lists. [othersHidden] is whether the server has libraries of other kinds, which the tab leaves out. */
    data class Ready(
        val tiles: List<LibraryTile>,
        val othersHidden: Boolean = false,
    ) : LibrariesUiState

    /** The user has libraries, but none this app lists (movies, series, collections and mixed libraries are). */
    data object Empty : LibrariesUiState

    data class Error(
        val failure: ServerFailure,
    ) : LibrariesUiState
}

/**
 * Whether the Libraries tab lists a library of [this] kind: movies, series, collections, and a mixed library (no collection type) that
 * holds movies and series together. Music, books, photos, home videos, playlists and live TV are left out, not greyed.
 */
fun LibraryKind.listed(): Boolean = this != LibraryKind.Other

/** Whether the server has a library the tab does not list. */
fun List<LibraryView>.hasUnlisted(): Boolean = any { !it.kind.listed() }

/** The libraries the tab lists, in the order the server gave them, as tiles. */
fun List<LibraryView>.tiles(): List<LibraryTile> = filter { it.kind.listed() }.map { it.tile() }

/** The picture a library is drawn with: its Thumb, else its first Backdrop (neither has a name in it), else its Primary, else none. */
private fun LibraryView.tile(): LibraryTile =
    when {
        thumbImageTag != null -> LibraryTile(id, name, kind, thumbImageTag, ImageKind.Thumb)
        backdropImageTag != null -> LibraryTile(id, name, kind, backdropImageTag, ImageKind.Backdrop)
        else -> LibraryTile(id, name, kind, primaryImageTag, ImageKind.Primary)
    }
