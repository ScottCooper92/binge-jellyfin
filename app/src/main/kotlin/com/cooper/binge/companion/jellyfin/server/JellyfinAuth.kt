package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** The server as it answers before sign-in. */
data class PublicServerInfo(
    val serverName: String,
    val version: String,
    val serverId: String,
)

/** A session the server minted: its token, and who it is for. */
data class SignedIn(
    val token: String,
    val userId: String,
    val userName: String,
    val serverId: String,
)

/** A Quick Connect request: the code the user approves, and the secret this app polls with. */
data class QuickConnectStart(
    val secret: String,
    val code: String,
)

suspend fun JellyfinClient.publicInfo(): PublicServerInfo {
    val json = getObject("System/Info/Public")
    return PublicServerInfo(
        serverName = json.string("ServerName").orEmpty(),
        version = json.string("Version").orEmpty(),
        serverId = json.string("Id").orEmpty(),
    )
}

suspend fun JellyfinClient.authenticateByName(
    username: String,
    password: String,
): SignedIn =
    postObject(
        "Users/AuthenticateByName",
        JsonObject(mapOf("Username" to JsonPrimitive(username), "Pw" to JsonPrimitive(password))),
    ).toSignedIn()

suspend fun JellyfinClient.quickConnectEnabled(): Boolean = (get("QuickConnect/Enabled") as? JsonPrimitive)?.booleanOrNull == true

suspend fun JellyfinClient.quickConnectInitiate(): QuickConnectStart {
    val json = postObject("QuickConnect/Initiate", null)
    return QuickConnectStart(secret = json.string("Secret").orEmpty(), code = json.string("Code").orEmpty())
}

suspend fun JellyfinClient.quickConnectAuthenticated(secret: String): Boolean =
    getObject("QuickConnect/Connect", mapOf("secret" to secret)).bool("Authenticated") == true

suspend fun JellyfinClient.authenticateWithQuickConnect(secret: String): SignedIn =
    postObject("Users/AuthenticateWithQuickConnect", JsonObject(mapOf("Secret" to JsonPrimitive(secret)))).toSignedIn()

/** Ends this device's session on the server, so it leaves the user's devices list. */
suspend fun JellyfinClient.logout() {
    post("Sessions/Logout", null)
}

suspend fun JellyfinClient.me(): String = getObject("Users/Me").string("Name").orEmpty()

private fun JsonObject.toSignedIn(): SignedIn {
    val user = obj("User")
    return SignedIn(
        token = string("AccessToken").orEmpty(),
        userId = user?.string("Id").orEmpty(),
        userName = user?.string("Name").orEmpty(),
        serverId = string("ServerId").orEmpty(),
    )
}
