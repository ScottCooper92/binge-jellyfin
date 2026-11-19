package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient

/** The titles in a library, paged, ordered and filtered by the server. Failures are the transport's, as in [LibraryRepository]. */
interface ItemsRepository {
    /**
     * One page of [library]'s titles. A page past the end is empty, not an error. [withTotal] asks the server to count the
     * whole list, which costs it a full query: ask only where the screen shows the count.
     */
    suspend fun items(
        library: LibraryView,
        sort: ItemSort = ItemSort(),
        filters: ItemFilters = ItemFilters(),
        startIndex: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
        withTotal: Boolean = false,
    ): Page<BrowseItem>

    /** The genres, years and ratings [library] has titles for. */
    suspend fun filterChoices(library: LibraryView): FilterChoices

    companion object {
        const val DEFAULT_PAGE_SIZE = 60
    }
}

/** [ItemsRepository] over the user's own server. */
class JellyfinItemsRepository(
    private val client: JellyfinClient,
    private val userId: String,
) : ItemsRepository {
    override suspend fun items(
        library: LibraryView,
        sort: ItemSort,
        filters: ItemFilters,
        startIndex: Int,
        limit: Int,
        withTotal: Boolean,
    ): Page<BrowseItem> = client.browseItems(userId, library, sort, filters, startIndex, limit, withTotal)

    override suspend fun filterChoices(library: LibraryView): FilterChoices = client.browseFilterChoices(userId, library)
}
