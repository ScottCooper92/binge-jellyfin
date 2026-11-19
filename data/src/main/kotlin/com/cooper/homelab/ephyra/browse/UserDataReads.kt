package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.UserDataChange
import com.cooper.homelab.ephyra.server.decodeUserData
import kotlinx.serialization.json.JsonObject

// The writes a viewer makes. 10.9 moved both routes and put the user in the query; the 10.8.13 document has
// `/Users/{userId}/PlayedItems/{itemId}` and `/Users/{userId}/FavoriteItems/{itemId}`. Each answers the item's `UserItemDataDto`.

/** Marks [itemId] played (POST) or unplayed (DELETE), and answers the user data the server holds. */
internal suspend fun JellyfinClient.browseSetPlayed(
    userId: String,
    profile: JellyfinServerProfile,
    itemId: String,
    played: Boolean,
): UserDataChange = writeUserData(userId, profile, "Played", itemId, played)

/** Marks [itemId] a favourite (POST) or not (DELETE), and answers the user data the server holds. */
internal suspend fun JellyfinClient.browseSetFavourite(
    userId: String,
    profile: JellyfinServerProfile,
    itemId: String,
    favourite: Boolean,
): UserDataChange = writeUserData(userId, profile, "Favorite", itemId, favourite)

private suspend fun JellyfinClient.writeUserData(
    userId: String,
    profile: JellyfinServerProfile,
    kind: String,
    itemId: String,
    on: Boolean,
): UserDataChange {
    val (path, query) =
        if (profile.hasUserItemRoutes) {
            "User${kind}Items/$itemId" to mapOf("userId" to userId)
        } else {
            "Users/$userId/${kind}Items/$itemId" to
                emptyMap()
        }
    val answer = if (on) post(path, null, query) else delete(path, query)
    // A server that answers with no body (a 204, say) did the write: it reads as a change that states nothing.
    return decodeUserData(if (answer is JsonObject) answer else JsonObject(emptyMap()), itemId, path)
}
