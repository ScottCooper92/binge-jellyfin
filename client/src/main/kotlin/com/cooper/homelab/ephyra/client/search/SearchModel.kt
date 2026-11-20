package com.cooper.homelab.ephyra.client.search

import com.binge.companion.sdk.attempt
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.paging.ResultPage
import com.cooper.homelab.core.presentation.search.PagedQuerySearch
import com.cooper.homelab.core.presentation.search.QUERY_DEBOUNCE_MS
import com.cooper.homelab.core.presentation.search.QueryHistory
import com.cooper.homelab.core.presentation.search.QuerySearchState
import com.cooper.homelab.core.presentation.search.TypeFilter
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemsRepository
import com.cooper.homelab.ephyra.browse.SearchRepository
import com.cooper.homelab.ephyra.client.TabModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Search tab over the user's own server: movies, series and episodes by name, paged by the server's start index, with people found
 * alongside on the first page (the server cannot page them before 12.0). The shared [PagedQuerySearch] holds the text, the debounce, the
 * paging and the history; a [TypeFilter] narrows what has loaded to a chip's kind, so a new query starts on All again. It uses no Android
 * type, and [scope] is the caller's, with no default. [initialQuery] is what the field held before a process death, searched when the
 * tab starts. No count is asked for: the chips carry none.
 */
class SearchModel(
    private val scope: CoroutineScope,
    search: SearchRepository,
    history: QueryHistory,
    classify: suspend (Throwable) -> ServerFailure,
    private val initialQuery: String = "",
    pageSize: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
    debounceMs: Long = QUERY_DEBOUNCE_MS,
) : TabModel<SearchUiState> {
    private val query =
        PagedQuerySearch<SearchHit, Int>(
            scope = scope,
            history = history,
            first = 0,
            fetch = { text, start ->
                val titles = search.titles(text, TITLE_KINDS, start, pageSize)
                // People come with the first page only, and a search for them that fails leaves the titles as they are.
                val people = if (start == 0) attempt { search.people(text, 0, pageSize).items }.getOrDefault(emptyList()) else emptyList()
                ResultPage(titles.items.map(SearchHit::Title) + people.map(SearchHit::Person), next = titles.nextStart(pageSize))
            },
            classify = classify,
            debounceMs = debounceMs,
            keyOf = { it.kind to it.id },
        )
    private val kinds = TypeFilter<SearchKind, SearchHit>(SearchKind.entries, SearchHit::kind)

    override val state: StateFlow<SearchUiState> =
        combine(query.state, query.query, kinds.selected) { result, text, selected ->
            SearchUiState(text, SearchType.of(selected), result.toBody(selected))
        }.stateIn(scope, SharingStarted.Lazily, SearchUiState("", SearchType.All, SearchBody.Idle(emptyList())))

    /** A search has no pull-to-refresh: a retry reads the query again. */
    override val refreshing: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    private fun QuerySearchState<SearchHit>.toBody(selected: SearchKind?): SearchBody =
        when (this) {
            is QuerySearchState.Idle -> SearchBody.Idle(recents)
            is QuerySearchState.Loading -> SearchBody.Loading
            is QuerySearchState.Results -> SearchBody.Ready(kinds.keep(items, selected), items.size, hasMore, appending, appendFailed)
            is QuerySearchState.NoResults -> SearchBody.NoResults(this.query)
            is QuerySearchState.Failed -> SearchBody.Failed(failure)
        }

    /** Searches what the field held before the process ended, if it held anything. */
    override fun start() {
        if (initialQuery.isNotBlank()) setText(initialQuery)
    }

    override fun refresh() = query.retry()

    /** The field was edited. A text that is not the query the chip was chosen for puts the chips back to All. */
    fun setText(text: String) {
        kinds.resetForQuery(text)
        query.setQuery(text)
    }

    fun clear() {
        kinds.reset()
        query.clear()
    }

    fun submit() = query.submit()

    fun retry() = query.retry()

    fun loadMore() = query.loadMore()

    /** A recent query was picked: it is searched now, without the debounce. */
    fun pickRecent(text: String) {
        setText(text)
        query.submit()
    }

    fun clearRecents() {
        scope.launch { query.forgetRecents() }
    }

    fun selectType(type: SearchType) = kinds.select(type.kind)

    private companion object {
        val TITLE_KINDS = setOf(ItemKind.Movie, ItemKind.Series, ItemKind.Episode)
    }
}
