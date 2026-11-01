package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A device signed in to the server, as `/Devices` lists it. Admin only: every device call needs elevation. */
data class ServerDevice(
    val id: String,
    /** The name the device gave itself. */
    val name: String,
    /** The admin's name for it, if they gave one. */
    val customName: String?,
    val app: String,
    val user: String,
    val lastSeenMillis: Long?,
) {
    /** What to call it: the admin's name, else the one the device gave. */
    val displayName: String get() = customName ?: name
}

/**
 * The devices, most recently seen first. The list leaves out the names an admin gave them (Jellyfin
 * 10.11 answers `CustomName` only per device), so each device's info is read too, all at once. A device
 * whose info can't be read keeps the name it gave itself.
 */
suspend fun JellyfinClient.devices(): List<ServerDevice> {
    val listed =
        getObject("Devices").array("Items").mapNotNull { element ->
            val json = element as? JsonObject ?: return@mapNotNull null
            val id = json.string("Id") ?: return@mapNotNull null
            ServerDevice(
                id = id,
                name = json.string("Name").orEmpty(),
                customName = null,
                app = json.string("AppName").orEmpty(),
                user = json.string("LastUserName").orEmpty(),
                lastSeenMillis = json.string("DateLastActivity")?.epochMillisOrNull(),
            )
        }
    return coroutineScope {
        listed
            .map { device -> async { device.named(customName(device.id)) } }
            .awaitAll()
            .sortedByDescending { it.lastSeenMillis ?: Long.MIN_VALUE }
    }
}

private suspend fun JellyfinClient.customName(id: String): String? =
    attempt { getObject("Devices/Info", mapOf("id" to id)) }.getOrNull()?.string("CustomName")?.takeIf { it.isNotBlank() }

private fun ServerDevice.named(custom: String?): ServerDevice = if (custom == null) this else copy(customName = custom)

/** Names [id] as [name] on the server; an empty name goes back to the one the device gave. */
suspend fun JellyfinClient.renameDevice(
    id: String,
    name: String,
) {
    post("Devices/Options", JsonObject(mapOf("CustomName" to JsonPrimitive(name))), mapOf("id" to id))
}

/** Removes [id]: its sessions end and its token stops working, so it has to sign in again. */
suspend fun JellyfinClient.removeDevice(id: String) {
    delete("Devices", mapOf("id" to id))
}
