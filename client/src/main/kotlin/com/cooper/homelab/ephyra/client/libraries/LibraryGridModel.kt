package com.cooper.homelab.ephyra.client.libraries

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.browse.SortFilterQuery
import com.cooper.homelab.core.presentation.browse.SortFilterState
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.core.presentation.paging.PagedList
import com.cooper.homelab.core.presentation.paging.ResultPage
import com.cooper.homelab.core.presentation.paging.toGridState
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.FilterChoices
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.ItemsRepository
import com.cooper.homelab.ephyra.browse.LibraryRepository
import com.cooper.homelab.ephyra.browse.LibraryView
import com.cooper.homelab.ephyra.client.TabModel
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What sits above a library's grid in every state: its name, the sort and filters applied, and what the library offers to filter by
 * once the sheet has asked ([choices] is null until then, so the sheet lists only the order and the fixed filters).
 */
data class LibraryGridControls(
    val libraryName: String?,
    val applied: LibraryFilterDraft,
    val choices: FilterChoices?,
)

/** What a library's grid shows: the paged titles, or why there are none, with the [LibraryGridControls] in every state. */
typealias LibraryGridUiState = GridState<LibraryGridControls, BrowseItem>

/**
 * One library's titles, paged by the server in the order and under the filters the user chose. The sort and filters are a
 * [SortFilterState] and the pages a [PagedList] keyed on it, so a change to either starts again from the first page; the list pages by
 * the server's start index. It uses no Android type, and [scope] and [dispatcher] are the caller's, with no default. The library is found
 * by [libraryId] among the user's views, and one the user cannot see is a not-found failure. The list is read when [state] is first
 * collected, and [start] only gets the library's name ahead of the first page.
 */
class LibraryGridModel(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val libraryId: String,
    private val libraries: LibraryRepository,
    private val items: ItemsRepository,
    classify: suspend (Throwable) -> ServerFailure,
    initial: LibraryFilterDraft = LibraryFilterDraft(),
    private val pageSize: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
) : TabModel<LibraryGridUiState> {
    private val sortFilter = SortFilterState(initial.filters, initial.sort)
    private val choices = MutableStateFlow<FilterChoices?>(null)
    private val libraryName = MutableStateFlow<String?>(null)

    /** The library once it has been read, so a retry reads it again only if the first read failed. */
    private var library: LibraryView? = null

    private val list =
        PagedList<SortFilterQuery<ItemFilters, ItemSort>, BrowseItem, Int>(
            scope = scope,
            query = sortFilter.query,
            first = 0,
            fetch = { query, startIndex ->
                val page = items.items(library(), query.sort, query.filters, startIndex, pageSize)
                ResultPage(page.items, next = page.nextStart(pageSize), total = page.total)
            },
            classify = classify,
            keyOf = { it.id },
        )

    override val state: StateFlow<LibraryGridUiState> =
        combine(list.state, sortFilter.query, choices, libraryName) { page, query, offered, name ->
            page.toGridState(LibraryGridControls(name, LibraryFilterDraft(query.sort, query.filters), offered))
        }.stateIn(scope, SharingStarted.Lazily, GridState.Loading(LibraryGridControls(null, initial, null)))

    /** A grid has no pull-to-refresh state of its own: a read again shows the first page loading, as a new filter does. */
    override val refreshing: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override fun start() {
        scope.launch(dispatcher) { attempt { library() } }
    }

    override fun refresh() = list.retry()

    fun retry() = list.retry()

    fun loadMore() = list.loadMore()

    /** The sheet changed the order and the filters: both, as one change, so the list starts again once. */
    fun apply(draft: LibraryFilterDraft) = sortFilter.set(draft.filters, draft.sort)

    /** The chip for filter [section] was removed. */
    fun clearChip(section: String) = apply(LibraryFilterDraft(sortFilter.query.value.sort, sortFilter.query.value.filters).without(section))

    /** Every filter off, the order kept. */
    fun clearFilters() = apply(LibraryFilterDraft(sortFilter.query.value.sort, sortFilter.query.value.filters).clearedFilters())

    /** The sheet is opening: reads what the library has titles for, once. A failure leaves the sheet with the fixed filters, and it tries again next time. */
    fun filtersOpened() {
        if (choices.value != null) return
        scope.launch(dispatcher) { attempt { items.filterChoices(library()) }.onSuccess { choices.value = it } }
    }

    private suspend fun library(): LibraryView =
        library ?: libraries.views().firstOrNull { it.id == libraryId }?.also {
            library = it
            libraryName.value = it.name
        } ?: throw JellyfinHttpException(NOT_FOUND, "/Library/$libraryId")

    private companion object {
        const val NOT_FOUND = 404
    }
}
