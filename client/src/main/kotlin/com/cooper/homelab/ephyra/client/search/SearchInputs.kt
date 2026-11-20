package com.cooper.homelab.ephyra.client.search

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemsRepository
import com.cooper.homelab.ephyra.browse.Page
import com.cooper.homelab.ephyra.browse.PersonResult
import com.cooper.homelab.ephyra.browse.SearchRepository

/** The fewest characters, after trimming, that a search is run for: one letter would match most of a library. The server states no minimum; this is the app's. */
const val MIN_SEARCH_LENGTH = 2

/** What a search is narrowed to. Studios are not here: the server's search has none. */
enum class SearchType(
    /** The kinds of title it asks for; empty for people. */
    val kinds: Set<ItemKind>,
) {
    All(setOf(ItemKind.Movie, ItemKind.Series, ItemKind.Episode)),
    Movies(setOf(ItemKind.Movie)),
    Series(setOf(ItemKind.Series)),
    Episodes(setOf(ItemKind.Episode)),
    People(emptySet()),
    ;

    val isPeople: Boolean get() = this == People
}

/** One row of a result list: a title or a person. */
sealed interface SearchHit {
    data class Title(
        val item: BrowseItem,
    ) : SearchHit

    data class Person(
        val person: PersonResult,
    ) : SearchHit
}

/**
 * A search that is worth running: the trimmed [term] and the [type]. What the field holds runs ahead of this ([SearchQuery.of]
 * answers null until there is enough typed), so a screen shows its idle state, not an empty list, for a term that is too short. It is a plain value,
 * so it can key whichever list state pages the results.
 */
data class SearchQuery(
    val term: String,
    val type: SearchType,
) {
    /** One page of the results, from [startIndex]. People cannot be paged before server 12.0, so a later page of them is empty there. */
    suspend fun read(
        repository: SearchRepository,
        startIndex: Int,
        limit: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
    ): Page<SearchHit> =
        if (type.isPeople) {
            repository.people(term, startIndex, limit).let { page -> Page(page.items.map(SearchHit::Person), page.total, page.startIndex) }
        } else {
            repository.titles(term, type.kinds, startIndex, limit).let { page ->
                Page(page.items.map(SearchHit::Title), page.total, page.startIndex)
            }
        }

    companion object {
        /** The query for [text] and [type], or null while [text] is blank or shorter than [MIN_SEARCH_LENGTH] once trimmed. */
        fun of(
            text: String,
            type: SearchType,
        ): SearchQuery? = text.trim().takeIf { it.length >= MIN_SEARCH_LENGTH }?.let { SearchQuery(it, type) }
    }
}
