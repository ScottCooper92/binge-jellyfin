package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.array
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.server.int
import com.cooper.homelab.ephyra.server.long
import com.cooper.homelab.ephyra.server.obj
import com.cooper.homelab.ephyra.server.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

// The reads behind the browse repositories. Each asks for only what a card shows, by naming `fields`, `enableUserData`,
// `enableImageTypes` and `imageTypeLimit` itself rather than taking the server's defaults, which are large on a big library.

/** The item fields a card needs beyond the ones the server always sends. */
internal const val CARD_FIELDS = "PrimaryImageAspectRatio"

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
    return getObject(path, query)
        .array("Items")
        .mapNotNull { it as? JsonObject }
        .mapNotNull { view ->
            val id = view.string("Id") ?: return@mapNotNull null
            LibraryView(
                id = id,
                name = view.string("Name").orEmpty(),
                kind = LibraryKind.fromServer(view.string("CollectionType")),
                primaryImageTag = view.obj("ImageTags")?.string("Primary"),
            )
        }
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
    return getObject("Items", query).toPage(startIndex, withTotal)
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
        getObject(
            "Items/Filters",
            mapOf("userId" to userId, "parentId" to library.id, "includeItemTypes" to library.kind.itemTypes()),
        )

    fun strings(key: String) = answer.array(key).mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
    return FilterChoices(
        genres = strings("Genres").sortedBy(String::lowercase),
        years = answer.array("Years").mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.sortedDescending(),
        officialRatings = strings("OfficialRatings").sortedBy(String::lowercase),
    )
}

/** The item types a library of this kind holds. */
internal fun LibraryKind.itemTypes(): String =
    when (this) {
        LibraryKind.Movies -> "Movie"
        LibraryKind.TvShows -> "Series"
        LibraryKind.BoxSets -> "BoxSet"
        LibraryKind.Other -> "Movie,Series"
    }

internal fun JsonObject.toPage(
    startIndex: Int,
    withTotal: Boolean,
): Page<BrowseItem> =
    Page(
        items = array("Items").mapNotNull { (it as? JsonObject)?.toBrowseItem() },
        total = if (withTotal) int("TotalRecordCount") else null,
        startIndex = startIndex,
    )

internal fun JsonObject.toBrowseItem(): BrowseItem? {
    val id = string("Id") ?: return null
    val tags = obj("ImageTags")
    val imageTags =
        buildMap {
            tags?.string("Primary")?.let { put(ImageKind.Primary, it) }
            tags?.string("Thumb")?.let { put(ImageKind.Thumb, it) }
            tags?.string("Logo")?.let { put(ImageKind.Logo, it) }
            (array("BackdropImageTags").firstOrNull() as? JsonPrimitive)?.content?.let { put(ImageKind.Backdrop, it) }
        }
    val data = obj("UserData")
    return BrowseItem(
        id = id,
        name = string("Name").orEmpty(),
        kind = ItemKind.fromServer(string("Type")),
        year = int("ProductionYear"),
        communityRating = (get("CommunityRating") as? JsonPrimitive)?.doubleOrNull,
        officialRating = string("OfficialRating"),
        runTimeTicks = long("RunTimeTicks"),
        imageTags = imageTags,
        primaryAspectRatio = (get("PrimaryImageAspectRatio") as? JsonPrimitive)?.doubleOrNull,
        seriesId = string("SeriesId"),
        seriesName = string("SeriesName"),
        seasonNumber = int("ParentIndexNumber"),
        episodeNumber = int("IndexNumber"),
        userData =
            ItemUserData(
                played = data?.bool("Played") ?: false,
                favourite = data?.bool("IsFavorite") ?: false,
                positionTicks = data?.long("PlaybackPositionTicks") ?: 0L,
                playedPercentage = (data?.get("PlayedPercentage") as? JsonPrimitive)?.doubleOrNull,
                unplayedCount = data?.int("UnplayedItemCount"),
            ),
    )
}
