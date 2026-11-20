package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

// The reads behind the browse repositories. Each asks for only what a card shows, by naming `fields`, `enableUserData`,
// `enableImageTypes` and `imageTypeLimit` itself rather than taking the server's defaults, which are large on a big library.

/**
 * The item fields a card needs beyond the ones the server always sends. `ChildCount` is what a box set holds, for its card's "N titles":
 * the server sends it only when asked for, and it is cheap.
 */
internal const val CARD_FIELDS = "PrimaryImageAspectRatio,ChildCount"

/** The pictures a grid cell or card can show; one of each, never the whole set. */
internal const val CARD_IMAGE_TYPES = "Primary,Thumb,Backdrop"
internal const val CARD_IMAGE_LIMIT = "1"

/** The query every card list shares. */
internal fun cardQuery(userId: String): MutableMap<String, String> =
    linkedMapOf(
        "userId" to userId,
        "fields" to CARD_FIELDS,
        "enableUserData" to "true",
        "enableImages" to "true",
        "enableImageTypes" to CARD_IMAGE_TYPES,
        "imageTypeLimit" to CARD_IMAGE_LIMIT,
    )

/** The libraries the user can see, in their home's order. 10.9 moved the route; an older server is asked the old way. */
internal suspend fun JellyfinClient.browseViews(
    userId: String,
    profile: JellyfinServerProfile,
): List<LibraryView> {
    val (path, query) =
        if (profile.hasUserViewsRoute) "UserViews" to mapOf("userId" to userId) else "Users/$userId/Views" to emptyMap()
    return decodeAnswer(ViewsWire.serializer(), getObject(path, query), path).items.mapNotNull { it.toLibraryView() }
}

/** One page of a library's titles, ordered and filtered by the server. */
internal suspend fun JellyfinClient.browseItems(
    userId: String,
    library: LibraryView,
    sort: ItemSort,
    filters: ItemFilters,
    startIndex: Int,
    limit: Int,
    withTotal: Boolean,
): Page<BrowseItem> {
    val query = cardQuery(userId)
    query["parentId"] = library.id
    query["recursive"] = "true"
    query["includeItemTypes"] = library.kind.itemTypes()
    query["sortBy"] = if (sort.field == SortField.Name) sort.field.serverName else "${sort.field.serverName},SortName"
    query["sortOrder"] = if (sort.ascending) "Ascending" else "Descending"
    query["startIndex"] = startIndex.toString()
    query["limit"] = limit.toString()
    query["enableTotalRecordCount"] = withTotal.toString()
    // Genres and ratings are `|`-separated and years `,`-separated, as the server's own clients send them.
    if (filters.genres.isNotEmpty()) query["genres"] = filters.genres.joinToString("|")
    if (filters.officialRatings.isNotEmpty()) query["officialRatings"] = filters.officialRatings.joinToString("|")
    if (filters.years.isNotEmpty()) query["years"] = filters.years.sorted().joinToString(",")
    filters.played?.let { query["isPlayed"] = it.toString() }
    if (filters.favouritesOnly) query["isFavorite"] = "true"
    return decodeAnswer(ItemsWire.serializer(), getObject("Items", query), "Items").toPage(startIndex, withTotal)
}

/**
 * What the library can be filtered by: `/Items/Filters`, not `/Items/Filters2`. The newer route names genres by id and
 * leaves out years and ratings, which a filter sheet needs; the older one is in the OpenAPI document at the floor and at latest.
 */
internal suspend fun JellyfinClient.browseFilterChoices(
    userId: String,
    library: LibraryView,
): FilterChoices {
    val answer =
        decodeAnswer(
            FiltersWire.serializer(),
            getObject(
                "Items/Filters",
                mapOf("userId" to userId, "parentId" to library.id, "includeItemTypes" to library.kind.itemTypes()),
            ),
            "Items/Filters",
        )
    return FilterChoices(
        genres = answer.genres.sortedBy(String::lowercase),
        years = answer.years.sortedDescending(),
        officialRatings = answer.officialRatings.sortedBy(String::lowercase),
    )
}

/** The item types a library of this kind holds. */
internal fun LibraryKind.itemTypes(): String =
    when (this) {
        LibraryKind.Movies -> "Movie"
        LibraryKind.TvShows -> "Series"
        LibraryKind.BoxSets -> "BoxSet"
        LibraryKind.Mixed, LibraryKind.Other -> "Movie,Series"
    }

/**
 * The item types a latest read asks for. A series has no latest-items container of its own, so a TV library is asked for
 * its episodes and the server groups them under their series.
 */
internal fun LibraryKind.latestItemTypes(): String =
    when (this) {
        LibraryKind.TvShows -> "Episode"
        LibraryKind.Mixed, LibraryKind.Other -> "Movie,Episode"
        else -> itemTypes()
    }
