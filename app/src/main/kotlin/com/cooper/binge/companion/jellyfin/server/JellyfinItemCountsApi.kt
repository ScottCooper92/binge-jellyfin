package com.cooper.binge.companion.jellyfin.server

/** How many movies and series a user can see. Each is null when the server didn't send it. */
data class ItemCounts(
    val movies: Int?,
    val series: Int?,
)

/**
 * How many movies and series a user can see, and how many of those carry no TMDB id. [LibraryIndex] finds a title only
 * by its TMDB id, so those [withoutTmdbId] can never be matched, whatever the file is called.
 */
data class TmdbCoverage(
    val titles: Int,
    val withoutTmdbId: Int,
)

/**
 * The user's [TmdbCoverage], from two reads of `/Items` that ask for one record each and take the server's total: the movies
 * and series, and those with no TMDB id (`hasTmdbId` off). It only reads: it never starts a scan or a refresh. Null when the
 * server does not send a total.
 */
suspend fun JellyfinClient.tmdbCoverage(userId: String): TmdbCoverage? {
    val titles = titleTotal(userId, withoutTmdbId = false) ?: return null
    val without = titleTotal(userId, withoutTmdbId = true) ?: return null
    return TmdbCoverage(titles, without)
}

/**
 * How many movies and series [userId] can see that carry no TMDB id, from one read of `/Items` that asks for one record and
 * takes the server's total. Null when the server does not send a total.
 */
suspend fun JellyfinClient.titlesWithoutTmdbId(userId: String): Int? = titleTotal(userId, withoutTmdbId = true)

private suspend fun JellyfinClient.titleTotal(
    userId: String,
    withoutTmdbId: Boolean,
): Int? {
    val query =
        mapOf(
            "userId" to userId,
            "recursive" to "true",
            "includeItemTypes" to "Movie,Series",
            "enableImages" to "false",
            "limit" to "1",
        )
    return getObject("Items", if (withoutTmdbId) query + ("hasTmdbId" to "false") else query).int("TotalRecordCount")
}

/**
 * The movies and series [userId]'s access lets them see, from `/Items/Counts`. The server counts per user
 * (`IsVirtualItem` off, so a missing episode doesn't add to it), and sends every kind in one answer, so this
 * is one call, not one query per kind.
 */
suspend fun JellyfinClient.itemCounts(userId: String): ItemCounts {
    val json = getObject("Items/Counts", mapOf("userId" to userId))
    return ItemCounts(movies = json.int("MovieCount"), series = json.int("SeriesCount"))
}
