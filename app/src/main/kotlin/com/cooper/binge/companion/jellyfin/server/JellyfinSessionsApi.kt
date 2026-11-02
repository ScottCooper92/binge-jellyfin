package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How the server feeds a stream, as `PlayState.PlayMethod` names it. */
enum class PlayMethod { DirectPlay, DirectStream, Transcode }

/**
 * Something playing on a session the signed-in user can see: an admin sees every user's, anyone
 * else their own. [title] is the movie or the series; [episode] is set for an episode.
 */
data class NowPlaying(
    val sessionId: String,
    val userName: String,
    val deviceName: String,
    val client: String,
    val title: String,
    val episode: String?,
    val positionTicks: Long,
    val runtimeTicks: Long?,
    val paused: Boolean,
    val method: PlayMethod,
    val transcodeReasons: List<String>,
    /** The session's owner, or null for a shared device nobody has signed in on. */
    val userId: String? = null,
    val deviceId: String = "",
    val supportsMediaControl: Boolean = false,
    val supportsRemoteControl: Boolean = false,
    val canSeek: Boolean = false,
    /** What the client says it takes, as `GeneralCommandType` names (`Mute`, `SetVolume`, `DisplayMessage`, ...). */
    val supportedCommands: Set<String> = emptySet(),
    /** The client's volume from 0 to 100, or null if it did not say. */
    val volumeLevel: Int? = null,
    val muted: Boolean = false,
) {
    /** How far through, from 0 to 1, or null with no runtime to measure against. */
    val progress: Float? get() = runtimeTicks?.takeIf { it > 0 }?.let { (positionTicks.toFloat() / it).coerceIn(0f, 1f) }
}

/** What's playing now, read once. */
suspend fun JellyfinClient.nowPlaying(): List<NowPlaying> {
    val answer =
        get(SESSIONS, mapOf("activeWithinSeconds" to ACTIVE_WITHIN_SECONDS)) as? JsonArray ?: throw JellyfinUnreadableAnswer(SESSIONS)
    return answer.toNowPlaying()
}

/**
 * What's playing, as the event socket pushes it: a socket of the page's own, subscribed with
 * `SessionsStart`, so the service's shared socket doesn't carry these pushes.
 */
fun JellyfinClient.nowPlayingFeed(): Flow<List<NowPlaying>> =
    socketMessages(subscribe = listOf(SESSIONS_START))
        .filter { it.string("MessageType") == "Sessions" }
        .map { (it["Data"] as? JsonArray ?: JsonArray(emptyList())).toNowPlaying() }

/**
 * The ids of the sessions the server would let the signed-in user control: `GET /Sessions?controllableByUserId=`, which
 * applies the user's remote-control and shared-device settings and drops sessions that take no remote control. It is the
 * only place the server applies them: the endpoints that send a command check nothing (`AssertCanControl` is empty in
 * 10.11), so the app asks here and never trusts a send to refuse.
 */
suspend fun JellyfinClient.controllableSessionIds(userId: String): Set<String> {
    val answer =
        get(SESSIONS, mapOf("activeWithinSeconds" to ACTIVE_WITHIN_SECONDS, "controllableByUserId" to userId)) as? JsonArray
            ?: throw JellyfinUnreadableAnswer(SESSIONS)
    return answer.mapNotNull { (it as? JsonObject)?.string("Id") }.toSet()
}

/** The playstate commands a session takes, as `POST /Sessions/{id}/Playing/{command}` names them. */
enum class PlaystateCommand { Pause, Unpause, PlayPause, Seek, NextTrack, PreviousTrack }

/** Sends [command] to the session [sessionId], and only it. [seekPositionTicks] is for [PlaystateCommand.Seek]. */
suspend fun JellyfinClient.sendPlaystate(
    sessionId: String,
    command: PlaystateCommand,
    seekPositionTicks: Long? = null,
) {
    post(
        "Sessions/$sessionId/Playing/${command.name}",
        null,
        seekPositionTicks?.let { mapOf("seekPositionTicks" to it.toString()) }.orEmpty(),
    )
}

/** A general command with no arguments: `Mute`, `Unmute`, `VolumeUp`, `VolumeDown`. Sends to [sessionId] only. */
suspend fun JellyfinClient.sendGeneralCommand(
    sessionId: String,
    command: String,
) {
    post("Sessions/$sessionId/Command/$command", null)
}

/** Sets the session's volume, `0` to `100`. */
suspend fun JellyfinClient.setSessionVolume(
    sessionId: String,
    level: Int,
) {
    val body =
        buildJsonObject {
            put("Name", "SetVolume")
            put("Arguments", buildJsonObject { put("Volume", level.coerceIn(0, MAX_VOLUME).toString()) })
        }
    post("Sessions/$sessionId/Command", body)
}

/** Shows a message on the session's screen for [timeoutMs]. A blank [header] is left for the server to fill in. */
suspend fun JellyfinClient.sendSessionMessage(
    sessionId: String,
    header: String,
    text: String,
    timeoutMs: Long,
) {
    val body =
        buildJsonObject {
            if (header.isNotBlank()) put("Header", header.trim())
            put("Text", text.trim())
            put("TimeoutMs", timeoutMs)
        }
    post("Sessions/$sessionId/Message", body)
}

private const val MAX_VOLUME = 100

/** Asks the session's client to stop playing. The client stops; the session stays. */
suspend fun JellyfinClient.stopPlaying(sessionId: String) {
    post("Sessions/$sessionId/Playing/Stop", null)
}

private fun JsonArray.toNowPlaying(): List<NowPlaying> = mapNotNull { (it as? JsonObject)?.nowPlayingOrNull() }

/** A session as [NowPlaying], or null when it isn't playing anything. */
internal fun JsonObject.nowPlayingOrNull(): NowPlaying? {
    val item = obj("NowPlayingItem") ?: return null
    val id = string("Id") ?: return null
    val state = obj("PlayState")
    val series = item.string("SeriesName")
    return NowPlaying(
        sessionId = id,
        userName = string("UserName").orEmpty(),
        deviceName = string("DeviceName").orEmpty(),
        client = string("Client").orEmpty(),
        title = series ?: item.string("Name").orEmpty(),
        episode = series?.let { item.episodeLine() },
        positionTicks = state?.long("PositionTicks") ?: 0,
        runtimeTicks = item.long("RunTimeTicks"),
        paused = state?.bool("IsPaused") == true,
        method = PlayMethod.entries.firstOrNull { it.name == state?.string("PlayMethod") } ?: PlayMethod.DirectPlay,
        transcodeReasons = obj("TranscodingInfo")?.array("TranscodeReasons")?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty(),
        userId = string("UserId")?.takeUnless { it.isBlank() || it.all { c -> c == '0' || c == '-' } },
        deviceId = string("DeviceId").orEmpty(),
        supportsMediaControl = bool("SupportsMediaControl") == true,
        supportsRemoteControl = bool("SupportsRemoteControl") == true,
        canSeek = state?.bool("CanSeek") == true,
        supportedCommands = array("SupportedCommands").mapNotNull { (it as? JsonPrimitive)?.content }.toSet(),
        volumeLevel = state?.int("VolumeLevel"),
        muted = state?.bool("IsMuted") == true,
    )
}

/** `S1:E2 · Name`, or as much of it as the server gave. */
private fun JsonObject.episodeLine(): String {
    val season = int("ParentIndexNumber")
    val number = int("IndexNumber")
    val code = if (season != null && number != null) "S$season:E$number" else null
    return listOfNotNull(code, string("Name")).joinToString(" · ")
}

private const val SESSIONS = "Sessions"

/** Sessions seen in the last 16 minutes, as Jellyfin's own dashboard asks. */
private const val ACTIVE_WITHIN_SECONDS = "960"

/** Push the sessions straight away, then every two seconds. */
private const val SESSIONS_START = """{"MessageType":"SessionsStart","Data":"0,2000"}"""
