package com.cooper.binge.companion.jellyfin.service

import android.util.Log
import com.binge.companion.contracts.library.v1.GetPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.GetPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.PlaybackDeviceProfile
import com.binge.companion.contracts.library.v1.PlaybackEvent
import com.binge.companion.contracts.library.v1.PlaybackMethod
import com.binge.companion.contracts.library.v1.ReportPlaybackRequest
import com.binge.companion.contracts.library.v1.SubtitleTrack
import com.binge.companion.contracts.v1.HttpHeader
import com.binge.companion.contracts.v1.PlaybackSource
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.DeviceProfiles
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.JellyfinItem
import com.cooper.binge.companion.jellyfin.server.array
import com.cooper.binge.companion.jellyfin.server.bool
import com.cooper.binge.companion.jellyfin.server.item
import com.cooper.binge.companion.jellyfin.server.long
import com.cooper.binge.companion.jellyfin.server.playbackInfo
import com.cooper.binge.companion.jellyfin.server.reportProgress
import com.cooper.binge.companion.jellyfin.server.reportStart
import com.cooper.binge.companion.jellyfin.server.reportStopped
import com.cooper.binge.companion.jellyfin.server.string
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * Playback through Binge's player: a source the server chose for the host's device profile, and the
 * plays reported back as Jellyfin sessions so the server keeps the resume point.
 *
 * The token travels in the source's `Authorization` header. It goes into the URL only for an installed
 * player (`player_package` set), which may not send headers, and into a subtitle URL, which the host
 * fetches without them (`CLAUDE.md` > The one rule).
 */
class PlaybackSources(
    private val now: () -> Instant,
) {
    suspend fun mint(
        session: JellyfinSession,
        client: JellyfinClient,
        item: JellyfinItem,
        request: GetPlaybackSourceRequest,
    ): GetPlaybackSourceResponse {
        val device = if (request.hasDevice()) request.device else null
        val info = client.playbackInfo(session.userId, item.id, playbackInfoBody(session.userId, device))
        info.string("ErrorCode")?.let { code ->
            throw StatusException(Status.FAILED_PRECONDITION.withDescription("Jellyfin refused playback: $code"))
        }
        val source = info.array("MediaSources").firstOrNull()?.jsonObject ?: throw notFound()
        val sitting =
            Sitting(
                itemId = item.id,
                mediaSourceId = source.string("Id") ?: item.id,
                playSessionId = info.string("PlaySessionId").orEmpty(),
                playMethod = playMethodOf(source),
            )
        val stream = stream(client, source, sitting, tokenInUrl = if (request.playerPackage.isNotEmpty()) session.token else null)
        val runtimeMillis = (source.long("RunTimeTicks") ?: item.runTimeTicks ?: 0L) / TICKS_PER_MILLI
        val resumeMillis = (client.item(session.userId, item.id) ?: item).userData?.takeUnless { it.played }?.positionTicks ?: 0L
        Log.i(TAG, "Minted ${item.id}: ${sitting.playMethod}")
        return GetPlaybackSourceResponse
            .newBuilder()
            .setSource(
                stream
                    .toBuilder()
                    .addHeaders(HttpHeader.newBuilder().setName("Authorization").setValue(client.authorization()))
                    .setExpiresAt(now().plusSeconds(SOURCE_LIFETIME_SECONDS).toTimestamp()),
            ).setPlaybackSessionId(sitting.encode())
            .setResumePositionMillis(resumeMillis / TICKS_PER_MILLI)
            .setRuntimeMillis(runtimeMillis)
            .setMethod(
                if (sitting.playMethod ==
                    TRANSCODE
                ) {
                    PlaybackMethod.PLAYBACK_METHOD_TRANSCODE
                } else {
                    PlaybackMethod.PLAYBACK_METHOD_DIRECT_PLAY
                },
            ).addAllSubtitles(subtitles(client, source, session.token))
            .build()
    }

    /** One report, as the Jellyfin session call its event maps to. */
    suspend fun report(
        client: JellyfinClient,
        request: ReportPlaybackRequest,
    ) {
        val sitting = Sitting.parse(request.playbackSessionId) ?: throw invalid("unknown playback_session_id")
        val ticks = request.positionMillis.coerceAtLeast(0L) * TICKS_PER_MILLI
        val body = sitting.body(ticks, paused = request.event == PlaybackEvent.PLAYBACK_EVENT_PAUSED)
        when (request.event) {
            PlaybackEvent.PLAYBACK_EVENT_STARTED -> client.reportStart(body)
            PlaybackEvent.PLAYBACK_EVENT_PROGRESS,
            PlaybackEvent.PLAYBACK_EVENT_PAUSED,
            PlaybackEvent.PLAYBACK_EVENT_RESUMED,
            -> client.reportProgress(body)
            PlaybackEvent.PLAYBACK_EVENT_STOPPED -> client.reportStopped(body)
            // The end of the item: the server marks it played once the position passes its own threshold.
            PlaybackEvent.PLAYBACK_EVENT_FINISHED -> {
                val end = request.runtimeMillis.takeIf { it > 0 }?.let { it * TICKS_PER_MILLI } ?: ticks
                client.reportStopped(sitting.body(end, paused = false))
            }
            else -> throw invalid("unknown event")
        }
    }

    private fun playbackInfoBody(
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

    /** The server's choice: a direct stream of the file when it can play as it is, else its HLS transcode. */
    private fun stream(
        client: JellyfinClient,
        source: JsonObject,
        sitting: Sitting,
        tokenInUrl: String?,
    ): PlaybackSource {
        val transcodingUrl = source.string("TranscodingUrl")
        if (sitting.playMethod == TRANSCODE && transcodingUrl != null) {
            return PlaybackSource
                .newBuilder()
                .setUrl(client.resolve(transcodingUrl).withApiKey(tokenInUrl))
                .setMimeType(HLS)
                .build()
        }
        val query =
            buildMap {
                put("static", "true")
                put("mediaSourceId", sitting.mediaSourceId)
                if (sitting.playSessionId.isNotEmpty()) put("playSessionId", sitting.playSessionId)
                tokenInUrl?.let { put(API_KEY, it) }
            }
        return PlaybackSource
            .newBuilder()
            .setUrl(client.url("Videos/${sitting.itemId}/stream", query).toString())
            .setMimeType(DeviceProfiles.mimeForContainer(source.string("Container")))
            .build()
    }

    /** The server's external text subtitles, each carrying the token: the host hands a subtitle URL to the player without headers. */
    private fun subtitles(
        client: JellyfinClient,
        source: JsonObject,
        token: String,
    ): List<SubtitleTrack> =
        source
            .array("MediaStreams")
            .map(JsonElement::jsonObject)
            .filter { it.string("Type") == "Subtitle" && it.string("DeliveryMethod") == "External" }
            .mapNotNull { stream ->
                val path = stream.string("DeliveryUrl") ?: return@mapNotNull null
                val mime = DeviceProfiles.subtitleMime(stream.string("Codec")) ?: return@mapNotNull null
                SubtitleTrack
                    .newBuilder()
                    .setUrl(client.resolve(path).withApiKey(token))
                    .setLanguage(stream.string("Language").orEmpty())
                    .setLabel(stream.string("DisplayTitle") ?: stream.string("Title").orEmpty())
                    .setMimeType(mime)
                    .setForced(stream.bool("IsForced") == true)
                    .setHearingImpaired(stream.bool("IsHearingImpaired") == true)
                    .build()
            }.take(MAX_SUBTITLES)

    private fun playMethodOf(source: JsonObject): String =
        when {
            source.bool("SupportsDirectPlay") == true -> "DirectPlay"
            source.bool("SupportsDirectStream") == true || source.string("TranscodingUrl") == null -> "DirectStream"
            else -> TRANSCODE
        }

    /** What a report needs to name the play to Jellyfin, carried in the opaque `playback_session_id`. */
    internal data class Sitting(
        val itemId: String,
        val mediaSourceId: String,
        val playSessionId: String,
        val playMethod: String,
    ) {
        fun encode(): String = listOf(itemId, mediaSourceId, playSessionId, playMethod).joinToString(SEPARATOR)

        fun body(
            ticks: Long,
            paused: Boolean,
        ): JsonObject =
            JsonObject(
                mapOf(
                    "ItemId" to JsonPrimitive(itemId),
                    "MediaSourceId" to JsonPrimitive(mediaSourceId),
                    "PlaySessionId" to JsonPrimitive(playSessionId),
                    "PlayMethod" to JsonPrimitive(playMethod),
                    "PositionTicks" to JsonPrimitive(ticks),
                    "CanSeek" to JsonPrimitive(true),
                    "IsPaused" to JsonPrimitive(paused),
                ),
            )

        companion object {
            private const val SEPARATOR = "|"
            private const val FIELDS = 4

            fun parse(raw: String): Sitting? {
                val parts = raw.split(SEPARATOR)
                if (parts.size != FIELDS || parts.first().isEmpty()) return null
                return Sitting(itemId = parts[0], mediaSourceId = parts[1], playSessionId = parts[2], playMethod = parts[FIELDS - 1])
            }
        }
    }

    private companion object {
        const val TAG = "JellyfinPlayback"
        const val TRANSCODE = "Transcode"
        const val HLS = "application/x-mpegURL"
        const val API_KEY = "ApiKey"
        const val SOURCE_LIFETIME_SECONDS = 6 * 60 * 60L
        const val MAX_SUBTITLES = 10

        /** [this] with [token] as `ApiKey`, unless there is none to add or it already carries one. */
        fun String.withApiKey(token: String?): String {
            if (token == null || "ApiKey=" in this || "api_key=" in this) return this
            val separator = if ('?' in this) '&' else '?'
            return "$this$separator$API_KEY=$token"
        }
    }
}
