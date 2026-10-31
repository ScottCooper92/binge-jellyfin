package com.cooper.binge.companion.jellyfin.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
