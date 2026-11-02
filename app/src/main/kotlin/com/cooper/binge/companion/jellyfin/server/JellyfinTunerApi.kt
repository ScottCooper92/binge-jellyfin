package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Tuner hosts are written through `LiveTv/TunerHosts` (policy `LiveTvManagement`, so a manager need not be an administrator),
// read from the `TunerHosts` array of the Live TV configuration, and found on the network through `LiveTv/Tuners/Discover`.
// The server replaces a host by its `Id` whole, so an edit sends the host as it read it with the changes made, and a key this
// app does not know goes back as it came. A new host needs only a `Type`, an address and a name: the server's own defaults fill
// the rest. Read from the server's source at 10.8.13, 10.9.11, 10.10.7 and 10.11.0.

private const val TUNER_HOSTS = "LiveTv/TunerHosts"
private const val DISCOVER = "LiveTv/Tuners/Discover"

const val TUNER_ID = "Id"
const val TUNER_URL = "Url"
const val TUNER_TYPE = "Type"
const val TUNER_NAME = "FriendlyName"
const val TUNER_USER_AGENT = "UserAgent"
const val TUNER_COUNT = "TunerCount"
const val TUNER_FAVORITES_ONLY = "ImportFavoritesOnly"
const val TUNER_HW_TRANSCODING = "AllowHWTranscoding"

/** The two kinds of tuner this app adds, by the name the server gives them. A plugin's kind is shown and kept, never added. */
const val TUNER_TYPE_HDHOMERUN = "hdhomerun"
const val TUNER_TYPE_M3U = "m3u"

/**
 * Saves a tuner host: a new one has no `Id` and the server gives it one, an existing one is replaced by its `Id`. The server
 * checks the host before it keeps it (an HDHomeRun answers, an M3U playlist downloads), and answers an error if it cannot.
 */
suspend fun JellyfinClient.saveTunerHost(host: JsonObject): JsonObject = postObject(TUNER_HOSTS, host)

suspend fun JellyfinClient.deleteTunerHost(id: String) {
    delete(TUNER_HOSTS, mapOf("id" to id))
}

/** The tuners the server finds on the network that it does not have yet. The server listens for a few seconds before it answers. */
suspend fun JellyfinClient.discoverTuners(): List<JsonObject> =
    (get(DISCOVER, mapOf("newDevicesOnly" to "true")) as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

/** A host to add: its [type], its [url] and [name] (blank leaves the server to name it by its address). */
fun newTunerHost(
    type: String,
    url: String,
    name: String,
): JsonObject = JsonObject(mapOf(TUNER_TYPE to JsonPrimitive(type), TUNER_URL to JsonPrimitive(url), TUNER_NAME to JsonPrimitive(name)))

/** [host] with [key] set to [value]. */
fun JsonObject.with(
    key: String,
    value: JsonElement,
): JsonObject = JsonObject(this + (key to value))
