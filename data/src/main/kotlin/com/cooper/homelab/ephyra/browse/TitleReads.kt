package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

// The reads behind a title page. The title itself is the server's item route, which sends the whole item and takes no
// `fields`; the lists (seasons, episodes) name what they want as the card reads do.

private const val SEASON_FIELDS = "PrimaryImageAspectRatio,ChildCount"
private const val EPISODE_FIELDS = "PrimaryImageAspectRatio,Overview"

/** One title with everything a page shows. A missing id is the server's 404, thrown as the transport's error. */
internal suspend fun JellyfinClient.browseTitle(
    userId: String,
    profile: JellyfinServerProfile,
    id: String,
): TitleDetail? {
    // 10.9 moved the route and put the user in the query; an older server takes it in the path.
    val (path, query) =
        if (profile.hasUserItemRoutes) "Items/$id" to mapOf("userId" to userId) else "Users/$userId/Items/$id" to emptyMap()
    return decodeAnswer(ItemWire.serializer(), getObject(path, query), path).toTitleDetail()
}

/** A series' seasons, in the server's order. Specials are season 0 and the server decides where they come. */
internal suspend fun JellyfinClient.browseSeasons(
    userId: String,
    seriesId: String,
): List<BrowseItem> {
    val path = "Shows/$seriesId/Seasons"
    val query = cardQuery(userId).also { it["fields"] = SEASON_FIELDS }
    return decodeAnswer(ItemsWire.serializer(), getObject(path, query), path).items.mapNotNull { it.toBrowseItem() }
}

/** A series' episodes, all of them or those of one season, in the server's order. */
internal suspend fun JellyfinClient.browseEpisodes(
    userId: String,
    seriesId: String,
    seasonId: String?,
): List<BrowseItem> {
    val path = "Shows/$seriesId/Episodes"
    val query = cardQuery(userId).also { it["fields"] = EPISODE_FIELDS }
    seasonId?.let { query["seasonId"] = it }
    return decodeAnswer(ItemsWire.serializer(), getObject(path, query), path).items.mapNotNull { it.toBrowseItem() }
}

/**
 * What to play next, worked out from the episodes and the user's data rather than from `/Shows/NextUp`. That route is
 * documented only as "next up episodes": it leaves out a half-watched episode when `enableResumable` is off, and what it
 * does for a series with nothing watched, or all watched, is not in the document and has varied by version. Specials
 * (season 0) are not offered unless the series has nothing else.
 */
internal fun chooseNextEpisode(episodes: List<BrowseItem>): NextEpisode {
    if (episodes.isEmpty()) return NextEpisode.None
    val regular = episodes.filter { it.seasonNumber != 0 }.ifEmpty { episodes }
    regular.firstOrNull { !it.userData.played && it.userData.positionTicks > 0 }?.let { return NextEpisode.Resume(it) }
    val lastPlayed = regular.indexOfLast { it.userData.played }
    return when {
        lastPlayed < 0 -> NextEpisode.Start(regular.first())
        else ->
            regular.drop(lastPlayed + 1).firstOrNull { !it.userData.played }?.let { NextEpisode.Next(it) }
                ?: regular.firstOrNull { !it.userData.played }?.let { NextEpisode.Next(it) }
                ?: NextEpisode.AllWatched
    }
}
