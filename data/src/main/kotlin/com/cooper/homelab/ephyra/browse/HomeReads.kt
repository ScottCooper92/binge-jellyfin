package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import kotlinx.serialization.builtins.ListSerializer

// The reads behind Home: what the user is watching, what is next, and what is new. Each asks for what a card shows, as
// `BrowseReads.kt` does.

/** What the user started and did not finish: movies and episodes, most recently played first. */
internal suspend fun JellyfinClient.browseResume(
    userId: String,
    profile: JellyfinServerProfile,
    library: LibraryView?,
    startIndex: Int,
    limit: Int,
    withTotal: Boolean,
): Page<BrowseItem> {
    val query = cardQuery(userId)
    query["mediaTypes"] = "Video"
    query["includeItemTypes"] = "Movie,Episode"
    query["startIndex"] = startIndex.toString()
    query["limit"] = limit.toString()
    query["enableTotalRecordCount"] = withTotal.toString()
    library?.let { query["parentId"] = it.id }
    // 10.9 moved the route and put the user in the query; an older server takes it in the path.
    val path = if (profile.hasUserItemRoutes) "UserItems/Resume" else "Users/$userId/Items/Resume"
    return decodeAnswer(ItemsWire.serializer(), getObject(path, query), path).toPage(startIndex, withTotal)
}

/**
 * The next episode of each series the user is watching. A series with an episode half-watched is in continue watching, so
 * `enableResumable` and `enableRewatching` are off, as the server's own web client sends them (a server that has no such
 * parameter ignores it).
 */
internal suspend fun JellyfinClient.browseNextUp(
    userId: String,
    library: LibraryView?,
    startIndex: Int,
    limit: Int,
    withTotal: Boolean,
): Page<BrowseItem> {
    val query = cardQuery(userId)
    query["startIndex"] = startIndex.toString()
    query["limit"] = limit.toString()
    query["enableTotalRecordCount"] = withTotal.toString()
    query["enableResumable"] = "false"
    query["enableRewatching"] = "false"
    library?.let { query["parentId"] = it.id }
    return decodeAnswer(ItemsWire.serializer(), getObject("Shows/NextUp", query), "Shows/NextUp").toPage(startIndex, withTotal)
}

/**
 * The newest titles in [library]. The answer is a bare array, not a page. With [groupItems] the server folds a series'
 * new episodes into the one series, as the home screen shows them.
 */
internal suspend fun JellyfinClient.browseLatest(
    userId: String,
    profile: JellyfinServerProfile,
    library: LibraryView,
    limit: Int,
    hidePlayed: Boolean,
    groupItems: Boolean,
): List<BrowseItem> {
    val query = cardQuery(userId)
    query["parentId"] = library.id
    query["includeItemTypes"] = library.kind.itemTypes()
    query["limit"] = limit.toString()
    query["groupItems"] = groupItems.toString()
    if (hidePlayed) query["isPlayed"] = "false"
    val path = if (profile.hasUserItemRoutes) "Items/Latest" else "Users/$userId/Items/Latest"
    return decodeAnswer(ListSerializer(ItemWire.serializer()), get(path, query), path).mapNotNull { it.toBrowseItem() }
}
