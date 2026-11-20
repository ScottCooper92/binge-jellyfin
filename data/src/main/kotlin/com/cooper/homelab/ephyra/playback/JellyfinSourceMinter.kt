package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.DeviceProfiles
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.array
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.server.item
import com.cooper.homelab.ephyra.server.long
import com.cooper.homelab.ephyra.server.playbackInfo
import com.cooper.homelab.ephyra.server.string
import com.cooper.homelab.player.api.PlayMethod
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlaySource
import com.cooper.homelab.player.api.PlayTrack
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private const val HLS = "application/x-mpegURL"
private const val DIRECT_PLAY = "DirectPlay"
private const val DIRECT_STREAM = "DirectStream"
private const val TRANSCODE = "Transcode"
private const val MAX_SUBTITLES = 10
private const val NOT_FOUND = 404
private const val FORBIDDEN = 403
private const val REFUSED = 409

/**
 * Asks the server for a source the player can play. `POST /Items/{itemId}/PlaybackInfo` carries the player's device profile as a
 * Jellyfin `DeviceProfile`, and the server answers with a media source: played as it is when the device can
 * (`GET /Videos/{id}/stream?static=true`), or as the server's own HLS transcode (`TranscodingUrl`) when it cannot. This app never
 * inspects the file.
 *
 * The token is in the `Authorization` header the source carries and in no URL: the player sends that header on every request it makes,
 * the subtitle files included, so a subtitle needs no `ApiKey` of its own.
 */
class JellyfinSourceMinter(
    private val client: JellyfinClient,
    private val userId: String,
) : SourceMinter {
    override suspend fun mint(
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        resumeMs: Long?,
    ): Minted {
        val resume = resumeMs ?: savedPosition(item.itemId)
        val info = client.playbackInfo(userId, item.itemId, body(profile), resume * TICKS_PER_MILLI)
        info.string("ErrorCode")?.let { throw JellyfinHttpException(if (it == "NotAllowed") FORBIDDEN else REFUSED, PLAYBACK_INFO) }
        val source = info.array("MediaSources").firstOrNull() as? JsonObject ?: throw JellyfinHttpException(NOT_FOUND, PLAYBACK_INFO)
        val method = playMethodOf(source)
        val mediaSourceId = source.string("Id") ?: item.itemId
        val playSessionId = info.string("PlaySessionId").orEmpty()
        val runtimeMs = (source.long("RunTimeTicks") ?: 0L) / TICKS_PER_MILLI
        val request =
            PlayRequest(
                sessionId = "",
                source = playSource(source, item.itemId, mediaSourceId, playSessionId, method),
                title = item.title,
                resumePositionMs = resume,
                runtimeMs = runtimeMs,
                subtitles = subtitles(source),
            )
        return Minted(request, JellyfinPlayHandle(item.itemId, mediaSourceId, playSessionId, method, runtimeMs))
    }

    /** Where the server has the user in the item: its resume point, or the start once the item is played. */
    private suspend fun savedPosition(itemId: String): Long =
        (
            client
                .item(userId, itemId)
                ?.userData
                ?.takeUnless { it.played }
                ?.positionTicks ?: 0L
        ) / TICKS_PER_MILLI

    private fun body(profile: PlaybackDeviceProfile): JsonObject {
        val device = profile.toContract()
        return JsonObject(
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
    }

    private fun playSource(
        source: JsonObject,
        itemId: String,
        mediaSourceId: String,
        playSessionId: String,
        method: String,
    ): PlaySource {
        val transcodingUrl = source.string("TranscodingUrl")
        val headers = listOf("Authorization" to client.authorization())
        if (method == TRANSCODE && transcodingUrl != null) {
            return PlaySource(client.resolve(transcodingUrl).withoutApiKey(), HLS, headers, method = PlayMethod.TRANSCODE)
        }
        val query =
            buildMap {
                put("static", "true")
                put("mediaSourceId", mediaSourceId)
                if (playSessionId.isNotEmpty()) put("playSessionId", playSessionId)
            }
        val mime = DeviceProfiles.mimeForContainer(source.string("Container"))
        return PlaySource(client.url("Videos/$itemId/stream", query).toString(), mime, headers, method = PlayMethod.DIRECT_PLAY)
    }

    /** The server's external text subtitles, which the player loads beside the source. Embedded ones stay in the stream. */
    private fun subtitles(source: JsonObject): List<PlayTrack> =
        source
            .array("MediaStreams")
            .filterIsInstance<JsonObject>()
            .filter { it.string("Type") == "Subtitle" && it.string("DeliveryMethod") == "External" }
            .mapNotNull { stream ->
                val path = stream.string("DeliveryUrl") ?: return@mapNotNull null
                val mime = DeviceProfiles.subtitleMime(stream.string("Codec")) ?: return@mapNotNull null
                PlayTrack(
                    url = client.resolve(path).withoutApiKey(),
                    language = stream.string("Language").orEmpty(),
                    label = stream.string("DisplayTitle") ?: stream.string("Title"),
                    mimeType = mime,
                    forced = stream.bool("IsForced") == true,
                    hearingImpaired = stream.bool("IsHearingImpaired") == true,
                )
            }.take(MAX_SUBTITLES)

    private fun playMethodOf(source: JsonObject): String =
        when {
            source.bool("SupportsDirectPlay") == true -> DIRECT_PLAY
            source.bool("SupportsDirectStream") == true || source.string("TranscodingUrl") == null -> DIRECT_STREAM
            else -> TRANSCODE
        }

    private companion object {
        const val PLAYBACK_INFO = "Items/{id}/PlaybackInfo"

        /** [this] without any `ApiKey` or `api_key` parameter: Jellyfin puts the token into the URLs it writes, which the header covers. */
        fun String.withoutApiKey(): String {
            val url = toHttpUrlOrNull() ?: return this
            val builder = url.newBuilder()
            url.queryParameterNames
                .filter { it.equals("ApiKey", ignoreCase = true) || it.equals("api_key", ignoreCase = true) }
                .forEach(builder::removeAllQueryParameters)
            return builder.build().toString()
        }
    }
}
