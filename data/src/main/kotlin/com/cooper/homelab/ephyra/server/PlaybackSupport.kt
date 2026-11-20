package com.cooper.homelab.ephyra.server

import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Jellyfin counts time in ticks of 100 ns; the contract and the player in milliseconds. */
const val TICKS_PER_MILLI = 10_000L

/** The play methods Jellyfin's session reports name. */
const val PLAY_METHOD_DIRECT_PLAY = "DirectPlay"
const val PLAY_METHOD_DIRECT_STREAM = "DirectStream"
const val PLAY_METHOD_TRANSCODE = "Transcode"

private const val API_KEY = "ApiKey"
private const val MAX_SUBTITLES = 10

/**
 * The body of `POST /Items/{itemId}/PlaybackInfo` for a device: its profile as a Jellyfin `DeviceProfile`, and every way of playing
 * allowed, so the server picks direct play, a direct stream or its transcode. One body for Binge's player and the app's own.
 */
fun playbackInfoBody(
    userId: String,
    device: PlaybackDeviceProfile?,
): JsonObject =
    JsonObject(
        mapOf(
            "UserId" to JsonPrimitive(userId),
            "DeviceProfile" to DeviceProfiles.toJellyfin(device),
            "MaxStreamingBitrate" to JsonPrimitive(DeviceProfiles.maxBitrate(device)),
            "EnableDirectPlay" to JsonPrimitive(true),
            "EnableDirectStream" to JsonPrimitive(true),
            "EnableTranscoding" to JsonPrimitive(true),
            "AllowVideoStreamCopy" to JsonPrimitive(true),
            "AllowAudioStreamCopy" to JsonPrimitive(true),
            "AutoOpenLiveStream" to JsonPrimitive(true),
        ),
    )

/** How the server chose to play a media source: as it is, as a stream copy, or as its own transcode. */
fun playMethodOf(source: JsonObject): String =
    when {
        source.bool("SupportsDirectPlay") == true -> PLAY_METHOD_DIRECT_PLAY
        source.bool("SupportsDirectStream") == true || source.string("TranscodingUrl") == null -> PLAY_METHOD_DIRECT_STREAM
        else -> PLAY_METHOD_TRANSCODE
    }

/**
 * The address of the file itself (`GET /Videos/{id}/stream?static=true`) for a media source. [token] goes into the address only
 * where the caller may not send a header: an installed player.
 */
fun JellyfinClient.directStreamUrl(
    itemId: String,
    mediaSourceId: String,
    playSessionId: String,
    token: String? = null,
): String =
    url(
        "Videos/$itemId/stream",
        buildMap {
            put("static", "true")
            put("mediaSourceId", mediaSourceId)
            if (playSessionId.isNotEmpty()) put("playSessionId", playSessionId)
            token?.let { put(API_KEY, it) }
        },
    ).toString()

/** [this] without any `ApiKey` or `api_key` parameter: Jellyfin puts the token into the URLs it writes, which the header covers. */
fun String.withoutApiKey(): String {
    val url = toHttpUrlOrNull() ?: return this
    val builder = url.newBuilder()
    url.queryParameterNames
        .filter { it.equals(API_KEY, ignoreCase = true) || it.equals("api_key", ignoreCase = true) }
        .forEach(builder::removeAllQueryParameters)
    return builder.build().toString()
}

/**
 * [this] with [token] as `ApiKey`, unless there is none to add or it already carries one. The token goes into an address in
 * exactly two places (`CLAUDE.md`, the one rule): a source minted for an installed player, and a subtitle URL handed to Binge's player.
 */
fun String.withApiKey(token: String?): String {
    if (token == null || "ApiKey=" in this || "api_key=" in this) return this
    val separator = if ('?' in this) '&' else '?'
    return "$this$separator$API_KEY=$token"
}

/** One external text subtitle a media source offers: where the server serves it, and what it is. */
data class ExternalSubtitle(
    val path: String,
    val mimeType: String,
    val language: String,
    val label: String?,
    val forced: Boolean,
    val hearingImpaired: Boolean,
)

/**
 * The media source's external text subtitles in a format a player parses, at most ten. Embedded ones stay in the stream, and a
 * format no player here reads is left out.
 */
fun JsonObject.externalSubtitles(): List<ExternalSubtitle> =
    array("MediaStreams")
        .filterIsInstance<JsonObject>()
        .filter { it.string("Type") == "Subtitle" && it.string("DeliveryMethod") == "External" }
        .mapNotNull { stream ->
            val path = stream.string("DeliveryUrl") ?: return@mapNotNull null
            val mime = DeviceProfiles.subtitleMime(stream.string("Codec")) ?: return@mapNotNull null
            ExternalSubtitle(
                path = path,
                mimeType = mime,
                language = stream.string("Language").orEmpty(),
                label = stream.string("DisplayTitle") ?: stream.string("Title"),
                forced = stream.bool("IsForced") == true,
                hearingImpaired = stream.bool("IsHearingImpaired") == true,
            )
        }.take(MAX_SUBTITLES)

/**
 * Ends the transcode the server is running for [playSessionId] on this device (`DELETE /Videos/ActiveEncodings` with `deviceId` and
 * `playSessionId`), so a source that was replaced does not leave the server encoding for nobody. It leaves the play's state and resume
 * point alone, which a stopped report would not.
 */
suspend fun JellyfinClient.stopEncoding(playSessionId: String) {
    delete("Videos/ActiveEncodings", mapOf("deviceId" to identity.deviceId, "playSessionId" to playSessionId))
}

/**
 * The body of a session report (`/Sessions/Playing`, `/Progress`, `/Stopped`): the item, the media source, the play session, the play
 * method and where the play is.
 */
fun playReportBody(
    itemId: String,
    mediaSourceId: String,
    playSessionId: String,
    playMethod: String,
    positionTicks: Long,
    paused: Boolean,
): JsonObject =
    JsonObject(
        mapOf(
            "ItemId" to JsonPrimitive(itemId),
            "MediaSourceId" to JsonPrimitive(mediaSourceId),
            "PlaySessionId" to JsonPrimitive(playSessionId),
            "PlayMethod" to JsonPrimitive(playMethod),
            "PositionTicks" to JsonPrimitive(positionTicks),
            "CanSeek" to JsonPrimitive(true),
            "IsPaused" to JsonPrimitive(paused),
        ),
    )
