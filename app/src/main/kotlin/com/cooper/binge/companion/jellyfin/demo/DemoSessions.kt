package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val TICKS_PER_MINUTE = 600_000_000L
private const val RUNTIME_MINUTES = 118
private const val POSITION_MINUTES = 42
private const val SERIES_MINUTES = 47
private const val SERIES_POSITION_MINUTES = 12
private const val VOLUME = 80
private const val EPISODE_NUMBER = 4
private const val COMMANDS = "PlayPause,Pause,Unpause,Stop,Seek,SetVolume,Mute,Unmute"

/** The sessions the server has open: one playing a film, one playing an episode, one idle. Pausing and stopping change them. */
internal class DemoSessions : DemoState {
    private val sessions: MutableList<JsonObject> = seed()

    private fun seed() =
        mutableListOf(
            session("demo-session-1", DEMO_SAM_ID, "Sam", "Living room TV", "Jellyfin for Android TV", playing = film()),
            session("demo-session-2", DEMO_KIDS_ID, "Kids", "Tablet", "Jellyfin for Android", playing = episode()),
            session("demo-session-3", DEMO_USER_ID, DEMO_USER_NAME, "Pixel", "Ephyra", playing = null),
        )

    override fun reset() {
        sessions.clear()
        sessions.addAll(seed())
    }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Sessions") { _, _ -> ok(sessions.toList()) },
            route("POST", "Sessions", "{}", "Playing", "Stop") { _, (id) -> change(id) { null } },
            route("POST", "Sessions", "{}", "Playing", "{}") { _, (id, command) -> change(id) { playstate(it, command) } },
            route("POST", "Sessions", "{}", "Command") { call, (id) -> change(id) { volume(it, call.jsonBody()) } },
            route("POST", "Sessions", "{}", "Command", "{}") { _, (id, command) -> change(id) { general(it, command) } },
            route("POST", "Sessions", "{}", "Message") { _, _ -> noContent },
        )

    /** Applies [edit] to the session [id], dropping its now-playing item when [edit] answers null. */
    private fun change(
        id: String,
        edit: (JsonObject) -> JsonObject?,
    ): DemoAnswer {
        val index = sessions.indexOfFirst { it.string("Id") == id }
        if (index < 0) return demoNotFound
        val current = sessions[index]
        sessions[index] = edit(current) ?: JsonObject(current - "NowPlayingItem" - "PlayState")
        return noContent
    }

    private fun playstate(
        session: JsonObject,
        command: String,
    ): JsonObject {
        val state = session["PlayState"] as? JsonObject ?: return session
        val paused =
            when (command) {
                "Pause" -> true
                "Unpause" -> false
                "PlayPause" -> state.bool("IsPaused") != true
                else -> state.bool("IsPaused") == true
            }
        return session.with("PlayState", state.with("IsPaused", JsonPrimitive(paused)))
    }

    private fun volume(
        session: JsonObject,
        body: JsonObject,
    ): JsonObject {
        val state = session["PlayState"] as? JsonObject ?: return session
        val level = (body["Arguments"] as? JsonObject)?.string("Volume")?.toIntOrNull() ?: return session
        return session.with("PlayState", state.with("VolumeLevel", JsonPrimitive(level)))
    }

    private fun general(
        session: JsonObject,
        command: String,
    ): JsonObject {
        val state = session["PlayState"] as? JsonObject ?: return session
        return when (command) {
            "Mute" -> session.with("PlayState", state.with("IsMuted", JsonPrimitive(true)))
            "Unmute" -> session.with("PlayState", state.with("IsMuted", JsonPrimitive(false)))
            else -> session
        }
    }

    private fun film() = nowPlaying("demo-item-1", "The Long Voyage", "Movie", RUNTIME_MINUTES, POSITION_MINUTES)

    private fun episode() =
        nowPlaying("demo-item-2", "The Lighthouse", "Episode", SERIES_MINUTES, SERIES_POSITION_MINUTES, series = "Harbour Lights")

    private fun nowPlaying(
        id: String,
        name: String,
        type: String,
        runtime: Int,
        position: Int,
        series: String? = null,
    ) = buildJsonObject {
        putJsonObject("NowPlayingItem") {
            put("Id", id)
            put("Name", name)
            put("Type", type)
            put("RunTimeTicks", runtime * TICKS_PER_MINUTE)
            series?.let {
                put("SeriesName", it)
                put("ParentIndexNumber", 1)
                put("IndexNumber", EPISODE_NUMBER)
            }
        }
        putJsonObject("PlayState") {
            put("PositionTicks", position * TICKS_PER_MINUTE)
            put("IsPaused", false)
            put("CanSeek", true)
            put("PlayMethod", "DirectPlay")
            put("VolumeLevel", VOLUME)
            put("IsMuted", false)
        }
    }

    private fun session(
        id: String,
        userId: String,
        userName: String,
        device: String,
        client: String,
        playing: JsonObject?,
    ) = buildJsonObject {
        put("Id", id)
        put("UserId", userId)
        put("UserName", userName)
        put("DeviceName", device)
        put("DeviceId", "$id-device")
        put("Client", client)
        put("SupportsMediaControl", true)
        put("SupportsRemoteControl", true)
        putJsonArray("SupportedCommands") { COMMANDS.split(',').forEach { add(JsonPrimitive(it)) } }
        playing?.forEach { (key, value) -> put(key, value) }
    }
}
