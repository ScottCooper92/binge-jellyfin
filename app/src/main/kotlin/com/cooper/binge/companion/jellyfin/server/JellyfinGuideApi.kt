package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Guide data providers are written through `LiveTv/ListingProviders` (policy `LiveTvManagement`, so a manager need not be an
// administrator) and read from the `ListingProviders` array of the Live TV configuration. The server replaces a provider whole by its
// `Id`, so an edit sends it as it read it with the form's changes: a key the form does not show, and the `Password` a Schedules Direct
// account holds, go back as they came. A new provider needs only `Type` and what that kind takes. Saving or deleting one makes the
// server cancel a guide refresh that is running and queue a new one. Read from the server's source at 10.8.13, 10.9.11, 10.10.7 and
// 10.11.0: the save and the delete are the same at all four.

private const val LISTING_PROVIDERS = "LiveTv/ListingProviders"

const val GUIDE_ID = "Id"
const val GUIDE_TYPE = "Type"
const val GUIDE_PATH = "Path"

/** The guide provider kind this app adds with a path or an address: XMLTV, by the name the server gives it. */
const val GUIDE_TYPE_XMLTV = "xmltv"

/**
 * Saves a guide provider: a new one has no `Id` and the server gives it one, an existing one is replaced by its `Id`. The server
 * checks the provider before it keeps it (an XMLTV path must be an address or a file that exists) and answers an error if not.
 * Nothing is sent in the address: a Schedules Direct password goes in the body, hashed, and never as `pw`.
 */
suspend fun JellyfinClient.saveListingProvider(provider: JsonObject): JsonObject = postObject(LISTING_PROVIDERS, provider)

suspend fun JellyfinClient.deleteListingProvider(id: String) {
    delete(LISTING_PROVIDERS, mapOf("id" to id))
}

/** The providers as the server holds them, by `Id`: what an edit sends back with its changes. Never shown whole, as a `Password` is in it. */
suspend fun JellyfinClient.listingProviders(): Map<String, JsonObject> = liveTvConfiguration().listingProviderHosts()

internal fun JsonObject.listingProviderHosts(): Map<String, JsonObject> =
    array("ListingProviders").mapNotNull { it as? JsonObject }.mapNotNull { host -> host.string(GUIDE_ID)?.let { it to host } }.toMap()

/** A new XMLTV provider for [path]. */
fun newXmlTvProvider(path: String): JsonObject =
    JsonObject(mapOf(GUIDE_TYPE to JsonPrimitive(GUIDE_TYPE_XMLTV), GUIDE_PATH to JsonPrimitive(path)))

/** [provider] with its address or path set to [path]. */
fun JsonObject.withPath(path: String): JsonObject = JsonObject(this + (GUIDE_PATH to JsonPrimitive(path)))
