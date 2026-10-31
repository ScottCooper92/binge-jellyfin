package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl

/** The server as it answers before sign-in. */
data class PublicServerInfo(
    val serverName: String,
    val version: String,
    val serverId: String,
    /**
     * The server's base as it answered, after any redirects: `https://…` for an address typed bare
     * whose http request was redirected. Sign-in and every later call use this one, since OkHttp turns
     * a redirected POST into a GET and drops `Authorization` on a change of scheme or host.
     */
    val baseUrl: HttpUrl,
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
    val (json, from) = getObjectFrom(PUBLIC_INFO)
    return PublicServerInfo(
        serverName = json.string("ServerName").orEmpty(),
        version = json.string("Version").orEmpty(),
        serverId = json.string("Id").orEmpty(),
        baseUrl = baseOf(from) ?: baseUrl,
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

private const val PUBLIC_INFO = "System/Info/Public"

/** The base [answered] sits under, or null for a redirect to somewhere that is not this call's path. */
private fun baseOf(answered: HttpUrl): HttpUrl? {
    val path = answered.encodedPath
    if (!path.endsWith("/$PUBLIC_INFO", ignoreCase = true)) return null
    return answered
        .newBuilder()
        .encodedPath(path.dropLast(PUBLIC_INFO.length))
        .query(null)
        .fragment(null)
        .build()
}
