package com.cooper.homelab.ephyra.browse

/** What a grid can order by. [serverName] is the server's `ItemSortBy`. */
enum class SortField(
    internal val serverName: String,
) {
    Name("SortName"),
    DateAdded("DateCreated"),
    ReleaseDate("PremiereDate"),
    Year("ProductionYear"),
    Rating("CommunityRating"),
    Runtime("Runtime"),
    LastPlayed("DatePlayed"),
    Random("Random"),
}

data class ItemSort(
    val field: SortField = SortField.Name,
    val ascending: Boolean = true,
)

/** The filters a grid offers. An empty or null one does not narrow. Several values of one filter are alternatives. */
data class ItemFilters(
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
    val officialRatings: Set<String> = emptySet(),
    /** True for only the watched, false for only the unwatched, null for both. */
    val played: Boolean? = null,
    val favouritesOnly: Boolean = false,
) {
    val isEmpty: Boolean get() = this == ItemFilters()
}

/** What a library offers to filter by, so a filter sheet lists only values that match something. */
data class FilterChoices(
    val genres: List<String>,
    val years: List<Int>,
    val officialRatings: List<String>,
)
