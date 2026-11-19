package com.cooper.homelab.ephyra.browse

/** What a library holds, from the server's `CollectionType`. A kind this app does not browse is [Other]. */
enum class LibraryKind {
    Movies,
    TvShows,
    BoxSets,
    Other,
    ;

    companion object {
        fun fromServer(collectionType: String?): LibraryKind =
            when (collectionType?.lowercase()) {
                "movies" -> Movies
                "tvshows" -> TvShows
                "boxsets" -> BoxSets
                else -> Other
            }
    }
}

/** One of the user's libraries (a view): the thing the Libraries tab lists. */
data class LibraryView(
    val id: String,
    val name: String,
    val kind: LibraryKind,
    /** The tag of the library's own picture, when it has one. See [ImageUrls]. */
    val primaryImageTag: String? = null,
)

/** The kind of one item. A type this app does not know is [Other], never a failure. */
enum class ItemKind {
    Movie,
    Series,
    Season,
    Episode,
    BoxSet,
    Other,
    ;

    companion object {
        fun fromServer(type: String?): ItemKind = entries.firstOrNull { it != Other && it.name.equals(type, ignoreCase = true) } ?: Other
    }
}

/** The pictures an item can have. [serverName] is the segment the server's image route takes. */
enum class ImageKind(
    val serverName: String,
) {
    Primary("Primary"),
    Backdrop("Backdrop"),
    Thumb("Thumb"),
    Logo("Logo"),
}

/** What the signed-in user has done with an item. */
data class ItemUserData(
    val played: Boolean = false,
    val favourite: Boolean = false,
    val positionTicks: Long = 0L,
    /** How far through it they are, 0 to 100, when the server says. */
    val playedPercentage: Double? = null,
    /** Episodes not yet watched, for a series or season. */
    val unplayedCount: Int? = null,
)

/**
 * What a card or a grid cell shows of a movie, series, season or episode. Everything here comes in the one list
 * read, so a card never needs a second request. A title page's own fields (overview, people, genres, streams)
 * are another read.
 */
data class BrowseItem(
    val id: String,
    val name: String,
    val kind: ItemKind,
    val year: Int? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    /** The tag of each picture the item has (the first, for backdrops): the cache key in an image URL. */
    val imageTags: Map<ImageKind, String> = emptyMap(),
    /** Width over height of the primary picture, when the server says. */
    val primaryAspectRatio: Double? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    /** The tag of the series' poster, for an episode's card that shows the series. */
    val seriesPrimaryImageTag: String? = null,
    /** An episode has no backdrop or thumb of its own to speak of; its series or season does. These name whose, and the tag. */
    val parentBackdropItemId: String? = null,
    val parentBackdropImageTag: String? = null,
    val parentThumbItemId: String? = null,
    val parentThumbImageTag: String? = null,
    /**
     * A season's own number is in [seasonNumber] (0 for specials); an episode has its season's in [seasonNumber] and its own
     * in [episodeNumber]. The repositories do not sort specials: the page that shows them decides where they go.
     */
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    /** The season an episode is in. */
    val seasonId: String? = null,
    /** The overview, on the reads that ask for it (episode lists); null on a card. A title page reads [TitleDetail]. */
    val overview: String? = null,
    /** How many children a season or series has (episodes, seasons), on the reads that ask for it. */
    val childCount: Int? = null,
    val userData: ItemUserData = ItemUserData(),
)

/**
 * One page of a list. [total] is the whole list's size, or null when the read did not ask the server to count (counting
 * costs the server a full query, so only a screen that shows the count asks).
 */
data class Page<T>(
    val items: List<T>,
    val total: Int?,
    val startIndex: Int,
) {
    /** The index to ask for next, or null when this page was the last as far as it can be told. */
    fun nextStart(pageSize: Int): Int? {
        val next = startIndex + items.size
        val more = if (total != null) next < total else items.size >= pageSize
        return next.takeIf { more && items.isNotEmpty() }
    }
}
