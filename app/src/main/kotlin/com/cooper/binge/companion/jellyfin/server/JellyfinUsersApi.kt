package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One of the server's users, as `/Users` lists them. Admin only. [policy] is the whole policy as the
 * server last gave it, kept so a change sends back everything it doesn't touch.
 */
data class ServerUser(
    val id: String,
    val name: String,
    val hasPassword: Boolean,
    val lastSeenMillis: Long?,
    val policy: JsonObject,
) {
    val administrator: Boolean get() = policy.bool(IS_ADMINISTRATOR) == true
    val disabled: Boolean get() = policy.bool(IS_DISABLED) == true
    val allLibraries: Boolean get() = policy.bool(ENABLE_ALL_FOLDERS) != false

    /** The libraries this user may see when [allLibraries] is off, by library id. */
    val libraries: Set<String> get() = policy.array(ENABLED_FOLDERS).mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
}

internal const val IS_ADMINISTRATOR = "IsAdministrator"
internal const val IS_DISABLED = "IsDisabled"
internal const val ENABLE_ALL_FOLDERS = "EnableAllFolders"
internal const val ENABLED_FOLDERS = "EnabledFolders"

suspend fun JellyfinClient.users(): List<ServerUser> {
    val answer = get(USERS) as? JsonArray ?: throw JellyfinUnreadableAnswer(USERS)
    return answer.mapNotNull { (it as? JsonObject)?.serverUser() }.sortedBy { it.name.lowercase() }
}

suspend fun JellyfinClient.user(id: String): ServerUser = getObject("Users/$id").serverUser() ?: throw JellyfinUnreadableAnswer("Users/$id")

/** Creates a user, who can see every library and isn't an administrator, as Jellyfin's own default. */
suspend fun JellyfinClient.createUser(
    name: String,
    password: String,
): ServerUser =
    postObject("Users/New", JsonObject(mapOf("Name" to JsonPrimitive(name), "Password" to JsonPrimitive(password)))).serverUser()
        ?: throw JellyfinUnreadableAnswer("Users/New")

/** Replaces [id]'s whole policy. Jellyfin refuses one that would leave the server with no administrator. */
suspend fun JellyfinClient.savePolicy(
    id: String,
    policy: JsonObject,
) {
    post("Users/$id/Policy", policy)
}

/** Sets [id]'s password as an administrator, with no current password asked; empty leaves them with none. */
suspend fun JellyfinClient.setPassword(
    id: String,
    password: String,
) {
    post("Users/Password", JsonObject(mapOf("NewPw" to JsonPrimitive(password))), mapOf("userId" to id))
}

/**
 * Deletes [id]. Never call it for the signed-in user: Jellyfin refuses, but only after signing that
 * user out everywhere, this app included.
 */
suspend fun JellyfinClient.deleteUser(id: String) {
    delete("Users/$id")
}

private fun JsonObject.serverUser(): ServerUser? {
    val id = string("Id") ?: return null
    return ServerUser(
        id = id,
        name = string("Name").orEmpty(),
        hasPassword = bool("HasPassword") == true,
        lastSeenMillis = string("LastActivityDate")?.epochMillisOrNull(),
        policy = obj("Policy") ?: JsonObject(emptyMap()),
    )
}

private const val USERS = "Users"
