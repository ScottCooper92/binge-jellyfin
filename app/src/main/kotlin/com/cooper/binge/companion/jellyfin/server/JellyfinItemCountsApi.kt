package com.cooper.binge.companion.jellyfin.server

/** How many movies and series a user can see. Each is null when the server didn't send it. */
data class ItemCounts(
    val movies: Int?,
    val series: Int?,
)

/**
 * The movies and series [userId]'s access lets them see, from `/Items/Counts`. The server counts per user
 * (`IsVirtualItem` off, so a missing episode doesn't add to it), and sends every kind in one answer, so this
 * is one call, not one query per kind.
 */
suspend fun JellyfinClient.itemCounts(userId: String): ItemCounts {
    val json = getObject("Items/Counts", mapOf("userId" to userId))
    return ItemCounts(movies = json.int("MovieCount"), series = json.int("SeriesCount"))
}
