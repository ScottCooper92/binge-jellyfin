package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The user's view of one item: played, where they stopped, and when. */
data class UserData(
    val played: Boolean,
    val positionTicks: Long,
    val lastPlayedDate: String?,
)

/** A movie, series or episode, as much of it as this app reads. */
data class JellyfinItem(
    val id: String,
    val name: String,
    val type: String,
    val tmdbId: Int?,
    val runTimeTicks: Long?,
    val userData: UserData?,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val locationType: String? = null,
)

/** One page of movies and series that carry a TMDB id, with their provider ids and the user's data, and the total. */
suspend fun JellyfinClient.libraryPage(
    userId: String,
    startIndex: Int,
    limit: Int,
): Pair<List<JellyfinItem>, Int> {
    val json =
        getObject(
            "Items",
            mapOf(
                "userId" to userId,
                "recursive" to "true",
                "includeItemTypes" to "Movie,Series",
                "hasTmdbId" to "true",
                "fields" to "ProviderIds",
                "enableUserData" to "true",
                "enableImages" to "false",
                "startIndex" to startIndex.toString(),
                "limit" to limit.toString(),
            ),
        )
    val items = json.itemsOf()
    return items to (json.int("TotalRecordCount") ?: items.size)
}

/** One item as the user sees it, or null when the server has no such item for them. */
suspend fun JellyfinClient.item(
    userId: String,
    itemId: String,
): JellyfinItem? =
    getObject("Items", mapOf("userId" to userId, "ids" to itemId, "enableUserData" to "true", "fields" to "ProviderIds"))
        .itemsOf()
        .firstOrNull()

/** Every episode of a series the server has a file for, with the user's data. */
suspend fun JellyfinClient.episodes(
    userId: String,
    seriesId: String,
): List<JellyfinItem> =
    getObject(
        "Shows/$seriesId/Episodes",
        mapOf("userId" to userId, "enableUserData" to "true", "isMissing" to "false", "enableImages" to "false"),
    ).itemsOf()
        .filter { it.locationType != "Virtual" }

/** One page of a list the server pages, and how many entries the whole list holds. */
data class ItemPage(
    val items: List<JellyfinItem>,
    val total: Int,
)

/** The user's continue watching: what they started and did not finish, from [startIndex]. */
suspend fun JellyfinClient.resumeItems(
    userId: String,
    startIndex: Int,
    limit: Int,
): ItemPage =
    getObject(
        "UserItems/Resume",
        mapOf(
            "userId" to userId,
            "startIndex" to startIndex.toString(),
            "limit" to limit.toString(),
            "mediaTypes" to "Video",
            "enableUserData" to "true",
        ),
    ).toPage()

/** The next episode of each series the user is watching, from [startIndex]. */
suspend fun JellyfinClient.nextUp(
    userId: String,
    startIndex: Int,
    limit: Int,
): ItemPage =
    getObject(
        "Shows/NextUp",
        mapOf(
            "userId" to userId,
            "startIndex" to startIndex.toString(),
            "limit" to limit.toString(),
            "enableUserData" to "true",
            "enableTotalRecordCount" to "true",
        ),
    ).toPage()

suspend fun JellyfinClient.setPlayed(
    userId: String,
    itemId: String,
    played: Boolean,
) {
    val path = "UserPlayedItems/$itemId"
    if (played) post(path, null, mapOf("userId" to userId)) else delete(path, mapOf("userId" to userId))
}

suspend fun JellyfinClient.playbackInfo(
    userId: String,
    itemId: String,
    body: JsonObject,
): JsonObject = postObject("Items/$itemId/PlaybackInfo", body, mapOf("userId" to userId))

suspend fun JellyfinClient.reportStart(body: JsonObject) {
    post("Sessions/Playing", body)
}

suspend fun JellyfinClient.reportProgress(body: JsonObject) {
    post("Sessions/Playing/Progress", body)
}

suspend fun JellyfinClient.reportStopped(body: JsonObject) {
    post("Sessions/Playing/Stopped", body)
}

/** The answer's `Items`; an entry that is not an object is skipped, as a field the server drops reads as absent. */
private fun JsonObject.itemsOf(): List<JellyfinItem> = array("Items").filterIsInstance<JsonObject>().map { it.toItem() }

private fun JsonObject.toItem(): JellyfinItem {
    val tmdb = obj("ProviderIds")?.entries?.firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }?.value
    val userData =
        obj("UserData")?.let {
            UserData(
                played = it.bool("Played") == true,
                positionTicks = it.long("PlaybackPositionTicks") ?: 0L,
                lastPlayedDate = it.string("LastPlayedDate"),
            )
        }
    return JellyfinItem(
        id = string("Id").orEmpty(),
        name = string("Name").orEmpty(),
        type = string("Type").orEmpty(),
        tmdbId = (tmdb as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
        runTimeTicks = long("RunTimeTicks"),
        userData = userData,
        seriesId = string("SeriesId"),
        seasonNumber = int("ParentIndexNumber"),
        episodeNumber = int("IndexNumber"),
        locationType = string("LocationType"),
    )
}

private fun JsonObject.toPage(): ItemPage {
    val items = itemsOf()
    return ItemPage(items, int("TotalRecordCount") ?: items.size)
}
