package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.JellyfinItemsRepository
import com.cooper.homelab.ephyra.browse.JellyfinLibraryRepository
import com.cooper.homelab.ephyra.client.libraries.LibraryFilterDraft
import com.cooper.homelab.ephyra.client.libraries.LibraryGridControls
import com.cooper.homelab.ephyra.client.libraries.LibraryGridModel
import com.cooper.homelab.ephyra.client.libraries.LibraryGridUiState
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.serverFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Holds one library's grid ([LibraryGridModel]) for as long as its screen is open, so a rotation keeps what is shown
 * ([SignedInTabViewModel]). [initial] is the order and filters the library opens with: what a process death saved, or the defaults. The
 * repositories are built for the user and the server's [profile]. With no session the grid is a "not connected" failure.
 */
class LibraryGridTabViewModel(
    connection: Connection,
    private val dispatcher: CoroutineDispatcher,
    private val profile: JellyfinServerProfile,
    private val libraryId: String,
    private val initial: LibraryFilterDraft,
) : SignedInTabViewModel<LibraryGridUiState, LibraryGridModel>(
        connection,
        dispatcher,
        loading = GridState.Loading(LibraryGridControls(null, initial, null)),
        notConnected = GridState.Error(LibraryGridControls(null, initial, null), ServerFailure.NotConnected),
    ) {
    override fun build(
        scope: CoroutineScope,
        session: JellyfinSession,
        client: JellyfinClient,
    ) = LibraryGridModel(
        scope = scope,
        dispatcher = dispatcher,
        libraryId = libraryId,
        libraries = JellyfinLibraryRepository(client, session.userId, profile),
        items = JellyfinItemsRepository(client, session.userId),
        classify = { it.serverFailure() },
        initial = initial,
    )

    /** The URL of [item]'s poster at [width] pixels, or null before the client is read or for a title with no poster. */
    fun imageUrl(
        item: BrowseItem,
        width: Int,
    ): String? = images?.of(item, ImageKind.Primary, width)

    fun retry() = model?.retry() ?: start()

    fun loadMore() = model?.loadMore()

    fun apply(draft: LibraryFilterDraft) = model?.apply(draft)

    fun clearChip(section: String) = model?.clearChip(section)

    fun clearFilters() = model?.clearFilters()

    fun filtersOpened() = model?.filtersOpened()
}
