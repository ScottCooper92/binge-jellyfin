package com.cooper.homelab.ephyra.client.search

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.PersonResult

/** What a search result is. Studios are not here: the server's search has none. */
enum class SearchKind {
    Movie,
    Series,
    Episode,
    Person,
}

/** One result of a search: a title or a person. */
sealed interface SearchHit {
    /** What the result is. */
    val kind: SearchKind

    /** The server's id for it, which is unique across titles and people. */
    val id: String

    data class Title(
        val item: BrowseItem,
    ) : SearchHit {
        override val id: String get() = item.id
        override val kind: SearchKind
            get() =
                when (item.kind) {
                    ItemKind.Series -> SearchKind.Series
                    ItemKind.Episode -> SearchKind.Episode
                    else -> SearchKind.Movie
                }
    }

    data class Person(
        val person: PersonResult,
    ) : SearchHit {
        override val id: String get() = person.id
        override val kind: SearchKind get() = SearchKind.Person
    }
}

/** The chips under the field: All, then one for each kind of result. */
enum class SearchType(
    /** The kind it narrows the results to, or null for All. */
    val kind: SearchKind?,
) {
    All(null),
    Movies(SearchKind.Movie),
    Series(SearchKind.Series),
    Episodes(SearchKind.Episode),
    People(SearchKind.Person),
    ;

    companion object {
        fun of(kind: SearchKind?): SearchType = entries.firstOrNull { it.kind == kind } ?: All
    }
}

/** What sits under the field. */
sealed interface SearchBody {
    /** Nothing searched, or too little typed: the recent queries, newest first. */
    data class Idle(
        val recents: List<String>,
    ) : SearchBody

    data object Loading : SearchBody

    /**
     * The results of the chosen type. [loaded] is how many have arrived across every type, so a page that adds nothing to the chosen type
     * still asks for more while there are more.
     */
    data class Ready(
        val hits: List<SearchHit>,
        val loaded: Int,
        val hasMore: Boolean,
        val appending: Boolean,
        val appendFailed: Boolean,
    ) : SearchBody

    data class NoResults(
        val query: String,
    ) : SearchBody

    data class Failed(
        val failure: ServerFailure,
    ) : SearchBody
}

/** What the Search tab shows: [text] is what the field holds, which runs ahead of the query being searched. */
data class SearchUiState(
    val text: String,
    val type: SearchType,
    val body: SearchBody,
)

/** The key a result has in the list: its kind and id, so a tap opens the right page without a look-up. */
fun searchHitKey(hit: SearchHit): String = "${hit.kind.name}:${hit.id}"

/** The kind and id a [searchHitKey] holds, or null for a key it did not make. */
fun parseSearchHitKey(key: String): Pair<SearchKind, String>? {
    val kind = SearchKind.entries.firstOrNull { key.startsWith("${it.name}:") } ?: return null
    return kind to key.removePrefix("${kind.name}:")
}
