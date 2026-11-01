package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One of the user's libraries as their home lists it: a view, in Jellyfin's terms. */
data class UserView(
    val id: String,
    val name: String,
    /** `movies`, `tvshows`, `music` and so on; null for a mixed library. */
    val collectionType: String? = null,
)

/**
 * How Jellyfin's clients lay out the user's home, from their configuration: the library order, the
 * libraries left out of My Media and of Latest, the ones grouped into Movies, Shows and the like, and
 * three display switches. Read with the server's defaults for anything missing.
 */
data class HomeLayout(
    val order: List<String> = emptyList(),
    val myMediaExcludes: Set<String> = emptySet(),
    val latestExcludes: Set<String> = emptySet(),
    val grouped: Set<String> = emptySet(),
    val hidePlayedInLatest: Boolean = true,
    val displayMissingEpisodes: Boolean = false,
    val displayCollectionsView: Boolean = false,
)

internal const val ORDERED_VIEWS = "OrderedViews"
internal const val MY_MEDIA_EXCLUDES = "MyMediaExcludes"
internal const val LATEST_EXCLUDES = "LatestItemsExcludes"
internal const val GROUPED_FOLDERS = "GroupedFolders"
internal const val HIDE_PLAYED_IN_LATEST = "HidePlayedInLatest"
internal const val DISPLAY_MISSING_EPISODES = "DisplayMissingEpisodes"
internal const val DISPLAY_COLLECTIONS_VIEW = "DisplayCollectionsView"

fun JsonObject.homeLayout(): HomeLayout =
    HomeLayout(
        order = ids(ORDERED_VIEWS),
        myMediaExcludes = ids(MY_MEDIA_EXCLUDES).toSet(),
        latestExcludes = ids(LATEST_EXCLUDES).toSet(),
        grouped = ids(GROUPED_FOLDERS).toSet(),
        hidePlayedInLatest = bool(HIDE_PLAYED_IN_LATEST) != false,
        displayMissingEpisodes = bool(DISPLAY_MISSING_EPISODES) == true,
        displayCollectionsView = bool(DISPLAY_COLLECTIONS_VIEW) == true,
    )

/** A list of ids as the configuration stores it, for a save. */
fun idList(ids: Iterable<String>): JsonArray = JsonArray(ids.map(::JsonPrimitive))

private fun JsonObject.ids(key: String): List<String> =
    array(key).mapNotNull {
        (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
    }

/**
 * Every library [userId] can see, in their home's order, including the ones left out of My Media.
 * 10.9 moved the call to `/UserViews` and kept the old route only for compatibility, so an older server
 * is asked the old way.
 */
suspend fun JellyfinClient.userViews(
    userId: String,
    profile: JellyfinServerProfile,
): List<UserView> {
    val (path, query) =
        if (profile.hasUserViewsRoute) "UserViews" to mapOf("userId" to userId) else "Users/$userId/Views" to emptyMap()
    return getObject(path, query + ("includeHidden" to "true"))
        .array("Items")
        .mapNotNull { it as? JsonObject }
        .mapNotNull { view ->
            val id = view.string("Id") ?: return@mapNotNull null
            UserView(id, view.string("Name").orEmpty(), view.string("CollectionType"))
        }
}

/** The libraries that can be grouped into Movies, Shows and the like: the ones `GroupedFolders` may name. */
suspend fun JellyfinClient.groupingOptions(
    userId: String,
    profile: JellyfinServerProfile,
): List<UserView> {
    val (path, query) =
        if (profile.hasUserViewsRoute) {
            "UserViews/GroupingOptions" to mapOf("userId" to userId)
        } else {
            "Users/$userId/GroupingOptions" to
                emptyMap()
        }
    val answer = get(path, query) as? JsonArray ?: throw JellyfinUnreadableAnswer(path)
    return answer.mapNotNull { it as? JsonObject }.mapNotNull { option ->
        UserView(option.string("Id") ?: return@mapNotNull null, option.string("Name").orEmpty())
    }
}
