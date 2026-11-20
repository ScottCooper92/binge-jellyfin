package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.client.libraries.LibrariesUiState
import com.cooper.homelab.ephyra.client.libraries.LibrariesViewModel
import com.cooper.homelab.ephyra.client.libraries.LibraryTile
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the Libraries tab's [LibrariesViewModel] for the life of the tab, so a rotation or a switch of tab keeps the tiles
 * ([SignedInTabViewModel]). The repository is built for the user and the server's [profile]. With no session the tab is a "not
 * connected" failure.
 */
class LibrariesTabViewModel(
    connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
) : SignedInTabViewModel<LibrariesUiState, LibrariesViewModel>(
        connection,
        dispatcher,
        loading = LibrariesUiState.Loading,
        notConnected = LibrariesUiState.Error(ServerFailure.NotConnected),
    ) {
    override fun build(
        scope: CoroutineScope,
        session: JellyfinSession,
        client: JellyfinClient,
    ) = LibrariesViewModel(
        scope = scope,
        dispatcher = dispatcher,
        libraries = JellyfinLibraryRepository(client, session.userId, profile),
        classify = { it.serverFailure() },
    )

    /** The URL of [tile]'s picture at [width] pixels, or null before the client is read or for a library with no picture. */
    fun imageUrl(
        tile: LibraryTile,
        width: Int,
    ): String? = tile.imageTag?.let { images?.of(tile.id, ImageKind.Primary, it, width) }
}
