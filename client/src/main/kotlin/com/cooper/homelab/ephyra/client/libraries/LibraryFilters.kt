package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.ephyra.browse.FilterChoices
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.SortField

/** What a library's grid can be ordered by, in the order the sheet lists them. The rest of [SortField] (a year, a random order) is not offered. */
val librarySortFields: List<SortField> =
    listOf(SortField.Name, SortField.DateAdded, SortField.ReleaseDate, SortField.Rating, SortField.Runtime, SortField.LastPlayed)

/** Which titles the watched filter keeps. */
enum class PlayedChoice {
    Any,
    Watched,
    Unwatched,
    ;

    /** The server's `isPlayed`: true, false, or null for both. */
    val played: Boolean? get() =
        when (this) {
            Any -> null
            Watched -> true
            Unwatched -> false
        }

    companion object {
        fun of(played: Boolean?): PlayedChoice =
            when (played) {
                null -> Any
                true -> Watched
                false -> Unwatched
            }
    }
}

/** The ids of the sections [LibraryFilterDraft.sections] returns. A screen maps each to its heading, and a value to its label. */
object LibraryFilterSection {
    const val SORT = "sort"
    const val DIRECTION = "direction"
    const val PLAYED = "played"
    const val FAVOURITES = "favourites"
    const val GENRES = "genres"
    const val RATINGS = "ratings"
    const val YEARS = "years"
}

/** The sections a box-set library's sheet offers. */
private val BOX_SET_SECTIONS = setOf(LibraryFilterSection.SORT, LibraryFilterSection.DIRECTION, LibraryFilterSection.FAVOURITES)

/** The option ids that are not a value the server named (a genre, a rating, a year are their own ids). */
object LibraryFilterOption {
    const val ASCENDING = "ascending"
    const val DESCENDING = "descending"
    const val ALL = "all"
    const val FAVOURITES_ONLY = "only"
}

/** One section of the filter sheet, as data: a screen turns it into the design system's section and words it. */
sealed interface FilterSection {
    val id: String

    /** One option chosen. [selected] is an option's id. */
    data class Single(
        override val id: String,
        val options: List<String>,
        val selected: String,
    ) : FilterSection

    /** Any number chosen, which are alternatives: none chosen does not narrow. */
    data class Multi(
        override val id: String,
        val options: List<String>,
        val chosen: Set<String>,
    ) : FilterSection
}

/**
 * The sort and filters while the sheet is open, and the changes the sheet makes to them. The sheet applies them as one change
 * ([filters] and [sort]), so the list starts again once. It holds no list and no paging: it plugs into whichever state holds the
 * applied query. [choices] is what the library has titles for, so a section lists only values that match something.
 */
data class LibraryFilterDraft(
    val sort: ItemSort = ItemSort(),
    val filters: ItemFilters = ItemFilters(),
) {
    /** What the pill and the sheet count: the filters set, and one more for an order other than the default. */
    val activeCount: Int get() = appliedCount + if (sort != ItemSort()) 1 else 0

    /** How many filters are set. The order is not counted here; see [activeCount]. */
    val appliedCount: Int
        get() =
            (if (filters.genres.isNotEmpty()) 1 else 0) +
                (if (filters.years.isNotEmpty()) 1 else 0) +
                (if (filters.officialRatings.isNotEmpty()) 1 else 0) +
                (if (filters.played != null) 1 else 0) +
                (if (filters.favouritesOnly) 1 else 0)

    /**
     * The sections the sheet draws for a library that has [choices]. A section with nothing to choose from is left out, and a box-set
     * [kind] has only the order and Favourites: watched, genres, years and ratings mean little for a collection.
     */
    fun sections(
        choices: FilterChoices,
        kind: LibraryKind? = null,
    ): List<FilterSection> =
        if (kind == LibraryKind.BoxSets) {
            allSections(choices).filter { it.id in BOX_SET_SECTIONS }
        } else {
            allSections(choices)
        }

    private fun allSections(choices: FilterChoices): List<FilterSection> =
        listOfNotNull(
            FilterSection.Single(
                LibraryFilterSection.SORT,
                librarySortFields.map { it.name },
                sort.field.takeIf { it in librarySortFields }?.name ?: SortField.Name.name,
            ),
            FilterSection.Single(
                LibraryFilterSection.DIRECTION,
                listOf(LibraryFilterOption.ASCENDING, LibraryFilterOption.DESCENDING),
                if (sort.ascending) LibraryFilterOption.ASCENDING else LibraryFilterOption.DESCENDING,
            ),
            FilterSection.Single(LibraryFilterSection.PLAYED, PlayedChoice.entries.map { it.name }, PlayedChoice.of(filters.played).name),
            FilterSection.Single(
                LibraryFilterSection.FAVOURITES,
                listOf(LibraryFilterOption.ALL, LibraryFilterOption.FAVOURITES_ONLY),
                if (filters.favouritesOnly) LibraryFilterOption.FAVOURITES_ONLY else LibraryFilterOption.ALL,
            ),
            choices.genres.takeIf { it.isNotEmpty() }?.let { FilterSection.Multi(LibraryFilterSection.GENRES, it, filters.genres) },
            choices.officialRatings.takeIf { it.isNotEmpty() }?.let {
                FilterSection.Multi(
                    LibraryFilterSection.RATINGS,
                    it,
                    filters.officialRatings,
                )
            },
            choices.years.takeIf { it.isNotEmpty() }?.let {
                FilterSection.Multi(LibraryFilterSection.YEARS, it.map(Int::toString), filters.years.mapTo(mutableSetOf(), Int::toString))
            },
        )

    /** A single-choice section's option was picked. An id or option the draft does not know changes nothing. */
    fun select(
        section: String,
        option: String,
    ): LibraryFilterDraft =
        when (section) {
            LibraryFilterSection.SORT ->
                SortField.entries
                    .firstOrNull {
                        it.name == option && it in librarySortFields
                    }?.let { copy(sort = sort.copy(field = it)) }
            LibraryFilterSection.DIRECTION ->
                when (option) {
                    LibraryFilterOption.ASCENDING -> copy(sort = sort.copy(ascending = true))
                    LibraryFilterOption.DESCENDING -> copy(sort = sort.copy(ascending = false))
                    else -> null
                }
            LibraryFilterSection.PLAYED ->
                PlayedChoice.entries
                    .firstOrNull {
                        it.name == option
                    }?.let { copy(filters = filters.copy(played = it.played)) }
            LibraryFilterSection.FAVOURITES ->
                when (option) {
                    LibraryFilterOption.ALL -> copy(filters = filters.copy(favouritesOnly = false))
                    LibraryFilterOption.FAVOURITES_ONLY -> copy(filters = filters.copy(favouritesOnly = true))
                    else -> null
                }
            else -> null
        } ?: this

    /** A multi-choice section's option was tapped: chosen if it was not, dropped if it was. */
    fun toggle(
        section: String,
        option: String,
    ): LibraryFilterDraft =
        when (section) {
            LibraryFilterSection.GENRES -> copy(filters = filters.copy(genres = filters.genres.toggled(option)))
            LibraryFilterSection.RATINGS -> copy(filters = filters.copy(officialRatings = filters.officialRatings.toggled(option)))
            LibraryFilterSection.YEARS ->
                option.toIntOrNull()?.let { copy(filters = filters.copy(years = filters.years.toggled(it))) }
                    ?: this
            else -> this
        }

    /** The filter [section] cleared, as the chip that stands for it is removed. The order and the other filters are kept. */
    fun without(section: String): LibraryFilterDraft =
        copy(
            filters =
                when (section) {
                    LibraryFilterSection.GENRES -> filters.copy(genres = emptySet())
                    LibraryFilterSection.RATINGS -> filters.copy(officialRatings = emptySet())
                    LibraryFilterSection.YEARS -> filters.copy(years = emptySet())
                    LibraryFilterSection.PLAYED -> filters.copy(played = null)
                    LibraryFilterSection.FAVOURITES -> filters.copy(favouritesOnly = false)
                    else -> filters
                },
        )

    /** Every filter off, and the order kept: what "Clear filters" on an empty grid does. */
    fun clearedFilters(): LibraryFilterDraft = copy(filters = ItemFilters())

    /** Clear all in the sheet: every filter off and the order back to the library's default, which is everything [activeCount] counts. */
    fun clearedAll(): LibraryFilterDraft = LibraryFilterDraft()

    private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
}
