package com.cooper.homelab.ephyra.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * What the server says a user's data for one item is now: from a write's own answer (`UserItemDataDto`) or from a
 * `UserDataChanged` socket message (its `UserDataList` holds the same objects). A field the server left out is null, and
 * means "not stated", never "false": an overlay keeps what it holds for it. No Android types, no page knowledge.
 */
data class UserDataChange(
    /** The user the data is for, when the message says (a socket message does; a write's answer does not). */
    val userId: String?,
    /** The item's id, as [sameItem] compares it: the server writes ids with and without dashes. */
    val itemId: String,
    val played: Boolean? = null,
    val favourite: Boolean? = null,
    val positionTicks: Long? = null,
    val playedPercentage: Double? = null,
    val unplayedCount: Int? = null,
)

/** Whether two ids name one item. The server writes a Guid as 32 hex digits or with dashes, in either case. */
fun sameItem(
    first: String,
    second: String,
): Boolean = first.replace("-", "").equals(second.replace("-", ""), ignoreCase = true)

private val WireJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@Serializable
internal data class UserItemDataWire(
    @SerialName("ItemId") val itemId: String? = null,
    @SerialName("Played") val played: Boolean? = null,
    @SerialName("IsFavorite") val isFavorite: Boolean? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
)

@Serializable
internal data class UserDataChangedWire(
    @SerialName("UserId") val userId: String? = null,
    @SerialName("UserDataList") val userDataList: List<UserItemDataWire> = emptyList(),
)

private fun UserItemDataWire.toChange(
    userId: String?,
    fallbackItemId: String? = null,
): UserDataChange? {
    val id = itemId ?: fallbackItemId ?: return null
    return UserDataChange(userId, id, played, isFavorite, playbackPositionTicks, playedPercentage, unplayedItemCount)
}

/**
 * The changes in a `UserDataChanged` socket message: `Data.UserDataList`, as the 12.4 OpenAPI document gives the message
 * (`UserDataChangedMessage` → `UserDataChangeInfo`). The 10.8.13 document has no socket messages; the `UserItemDataDto` in
 * its list is the same there. Unknown fields are ignored. A message of another type, or a body of the wrong shape, has none.
 */
internal fun JsonObject.userDataChanges(): List<UserDataChange> {
    if (string("MessageType") != "UserDataChanged") return emptyList()
    val data = obj("Data") ?: return emptyList()
    return try {
        WireJson.decodeFromJsonElement(UserDataChangedWire.serializer(), data).let { info ->
            info.userDataList.mapNotNull { it.toChange(info.userId) }
        }
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}

/** A write's answer: the item's `UserItemDataDto`. A shape that is not one is [JellyfinUnreadableAnswer] for [path]. */
internal fun decodeUserData(
    element: JsonElement,
    itemId: String,
    path: String,
): UserDataChange =
    try {
        WireJson.decodeFromJsonElement(UserItemDataWire.serializer(), element).toChange(null, itemId)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } ?: throw JellyfinUnreadableAnswer(path)
