package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject

/**
 * An API key the server holds, as `/Auth/Keys` lists it. Admin only.
 *
 * A key is a server credential. It is held in memory here, for as long as its page is open, because
 * revoking one names it in the request path; it is never written down, logged or put anywhere else, and
 * [toString] leaves it out so a stray log line cannot carry it.
 */
class ApiKey(
    val key: String,
    val app: String,
    val createdMillis: Long?,
) {
    /** The last four characters: all the list shows of a key. */
    val lastFour: String get() = key.takeLast(LAST_CHARS)

    override fun toString(): String = "ApiKey(app=$app, key=…$lastFour)"

    override fun equals(other: Any?): Boolean = other is ApiKey && other.key == key && other.app == app && other.createdMillis == createdMillis

    override fun hashCode(): Int = key.hashCode()

    private companion object {
        const val LAST_CHARS = 4
    }
}

/** The server's API keys, newest first. */
suspend fun JellyfinClient.apiKeys(): List<ApiKey> =
    getObject("Auth/Keys")
        .array("Items")
        .mapNotNull { (it as? JsonObject)?.apiKey() }
        .sortedByDescending { it.createdMillis ?: Long.MIN_VALUE }

private fun JsonObject.apiKey(): ApiKey? {
    val key = string("AccessToken") ?: return null
    return ApiKey(key, string("AppName").orEmpty(), string("DateCreated")?.epochMillisOrNull())
}

/**
 * Makes a key for [app]. Jellyfin answers with nothing, so the new key is the one the list now has that
 * it did not before; null if none can be told apart. The keys as they now stand come with it.
 */
suspend fun JellyfinClient.createApiKey(app: String): Pair<ApiKey?, List<ApiKey>> {
    val before = apiKeys().map { it.key }.toSet()
    post("Auth/Keys", null, mapOf("app" to app))
    val after = apiKeys()
    return after.firstOrNull { it.key !in before && it.app == app } to after
}

/**
 * Revokes [key]: anything using it is refused from then on. Jellyfin takes the key in the path, so the
 * request is named by a label without it: an error, and so a log, never holds the key.
 */
suspend fun JellyfinClient.revokeApiKey(key: String) {
    delete("Auth/Keys/$key", label = "Auth/Keys/{key}")
}
