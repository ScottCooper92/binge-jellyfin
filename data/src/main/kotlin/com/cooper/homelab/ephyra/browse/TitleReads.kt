package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// The reads behind a title page. The title itself is the server's item route, which sends the whole item and takes no
// `fields`; the lists (seasons, episodes) name what they want as the card reads do.

private const val SEASON_FIELDS = "PrimaryImageAspectRatio,ChildCount"
private const val EPISODE_FIELDS = "PrimaryImageAspectRatio,Overview"

// Before 12.0 no route says which box sets hold an item, so each box set is asked for its members: the page size of the box set list, and how many are asked at once.
private const val BOX_SET_PAGE = 100
private const val COLLECTION_SCAN_PARALLEL = 4

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
                ?: NextEpisode.AllWatched(regular.first())
    }
}

/** Titles like [id]: the server's `/Items/{id}/Similar`, which takes `limit` and `fields` and no image options. */
internal suspend fun JellyfinClient.browseSimilar(
    userId: String,
    id: String,
    limit: Int,
): List<BrowseItem> {
    val path = "Items/$id/Similar"
    val query = mapOf("userId" to userId, "limit" to limit.toString(), "fields" to CARD_FIELDS)
    return decodeAnswer(ItemsWire.serializer(), getObject(path, query), path).items.mapNotNull { it.toBrowseItem() }
}

/**
 * A person, read by item id through the item route. `/Persons/{name}` is the only person route at every version and it
 * addresses by name, which breaks on a slash or a repeated name; a person is an item with an id, and `People[].Id` gives it.
 */
internal suspend fun JellyfinClient.browsePerson(
    userId: String,
    profile: JellyfinServerProfile,
    id: String,
): PersonDetail? {
    val (path, query) =
        if (profile.hasUserItemRoutes) "Items/$id" to mapOf("userId" to userId) else "Users/$userId/Items/$id" to emptyMap()
    return decodeAnswer(ItemWire.serializer(), getObject(path, query), path).toPersonDetail()
}

/** The movies and series on this server a person appears in, paged, by name. */
internal suspend fun JellyfinClient.browseTitlesWith(
    userId: String,
    personId: String,
    startIndex: Int,
    limit: Int,
    withTotal: Boolean,
): Page<BrowseItem> {
    val query = cardQuery(userId)
    query["personIds"] = personId
    query["recursive"] = "true"
    query["includeItemTypes"] = "Movie,Series"
    query["sortBy"] = "SortName"
    query["sortOrder"] = "Ascending"
    query["startIndex"] = startIndex.toString()
    query["limit"] = limit.toString()
    query["enableTotalRecordCount"] = withTotal.toString()
    return decodeAnswer(ItemsWire.serializer(), getObject("Items", query), "Items").toPage(startIndex, withTotal)
}

/** A collection's parts, oldest first as the server's own clients show them: release year, premiere date, then name. */
internal suspend fun JellyfinClient.browseCollectionParts(
    userId: String,
    collectionId: String,
): List<BrowseItem> {
    val query = cardQuery(userId)
    query["parentId"] = collectionId
    query["sortBy"] = "ProductionYear,PremiereDate,SortName"
    query["sortOrder"] = "Ascending"
    return decodeAnswer(ItemsWire.serializer(), getObject("Items", query), "Items").items.mapNotNull { it.toBrowseItem() }
}

/**
 * The collections [id] is in. From 12.0 the server names them (`/Items/{id}/Collections`). Before it, no route or field does, so
 * the user's box sets are listed and each is asked for its members, once, and [memberships] holds the answer for the next page: a
 * hand-made box set with no provider ids is found the same way as any other, because membership is the server's own list of children.
 */
internal suspend fun JellyfinClient.browseCollectionsOf(
    userId: String,
    profile: JellyfinServerProfile,
    id: String,
    memberships: BoxSetMemberships,
): List<BrowseItem> {
    if (!profile.hasItemCollectionsRoute) return memberships.containing(this, userId, id)
    val path = "Items/$id/Collections"
    val query = cardQuery(userId)
    return decodeAnswer(ItemsWire.serializer(), getObject(path, query), path).items.mapNotNull { it.toBrowseItem() }
}

/** Every box set the user can see, in name order, a page at a time. `parentId` is left out: before 12.0 the server drops it for a lone BoxSet type. */
private suspend fun JellyfinClient.browseBoxSets(userId: String): List<BrowseItem> {
    val boxes = mutableListOf<BrowseItem>()
    while (true) {
        val query = cardQuery(userId)
        query["includeItemTypes"] = "BoxSet"
        query["recursive"] = "true"
        query["sortBy"] = "SortName"
        query["sortOrder"] = "Ascending"
        query["startIndex"] = boxes.size.toString()
        query["limit"] = BOX_SET_PAGE.toString()
        query["enableTotalRecordCount"] = "false"
        val page = decodeAnswer(ItemsWire.serializer(), getObject("Items", query), "Items").items.mapNotNull { it.toBrowseItem() }
        boxes += page
        if (page.size < BOX_SET_PAGE) return boxes
    }
}

/** Each of the user's box sets with the ids of its members, read four at a time. */
internal suspend fun JellyfinClient.browseBoxSetMembers(userId: String): List<BoxSetMembers> =
    coroutineScope {
        val gate = Semaphore(COLLECTION_SCAN_PARALLEL)
        browseBoxSets(userId)
            .map { box ->
                async {
                    val members =
                        gate.withPermit {
                            val query =
                                linkedMapOf(
                                    "userId" to userId,
                                    "parentId" to box.id,
                                    "enableImages" to "false",
                                    "enableUserData" to "false",
                                    "enableTotalRecordCount" to "false",
                                )
                            decodeAnswer(ItemsWire.serializer(), getObject("Items", query), "Items").items
                        }
                    BoxSetMembers(box, members.mapNotNullTo(mutableSetOf()) { it.id })
                }
            }.awaitAll()
    }
