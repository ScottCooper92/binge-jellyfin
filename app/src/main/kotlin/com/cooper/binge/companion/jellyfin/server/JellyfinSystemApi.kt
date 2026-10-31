package com.cooper.binge.companion.jellyfin.server

import com.binge.companion.sdk.attempt

/** The server itself, as `/System/Info` describes it. Admin only. */
data class ServerInfo(
    val name: String,
    val version: String,
    val address: String,
    val architecture: String,
    val operatingSystem: String,
    val updateAvailable: Boolean,
    val restartPending: Boolean,
    /** Whether the server can restart itself; one that can't has to be restarted where it runs. */
    val canRestart: Boolean,
)

suspend fun JellyfinClient.serverInfo(): ServerInfo {
    val json = getObject("System/Info")
    return ServerInfo(
        name = json.string("ServerName").orEmpty(),
        version = json.string("Version").orEmpty(),
        address = json.string("LocalAddress").orEmpty(),
        architecture = json.string("SystemArchitecture").orEmpty(),
        operatingSystem = (json.string("OperatingSystemDisplayName") ?: json.string("OperatingSystem")).orEmpty(),
        updateAvailable = json.bool("HasUpdateAvailable") == true,
        restartPending = json.bool("HasPendingRestart") == true,
        canRestart = json.bool("CanSelfRestart") == true,
    )
}

/** Asks the server to restart. Everyone using it is cut off until it's back, this app's sockets included. */
suspend fun JellyfinClient.restartServer() {
    post("System/Restart", null)
}

/** Whether the server answers at all, as it does to anyone, signed in or not. */
suspend fun JellyfinClient.answers(): Boolean = attempt { get("System/Info/Public") }.isSuccess
