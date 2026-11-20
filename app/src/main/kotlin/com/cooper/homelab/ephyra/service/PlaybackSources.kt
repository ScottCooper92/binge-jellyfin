package com.cooper.homelab.ephyra.service

import com.binge.companion.contracts.library.v1.GetPlaybackSourceRequest
import com.binge.companion.contracts.library.v1.GetPlaybackSourceResponse
import com.binge.companion.contracts.library.v1.PlaybackEvent
import com.binge.companion.contracts.library.v1.PlaybackMethod
import com.binge.companion.contracts.library.v1.ReportPlaybackRequest
import com.binge.companion.contracts.library.v1.SubtitleTrack
import com.binge.companion.contracts.library.v1.Trickplay
import com.binge.companion.contracts.v1.HttpHeader
import com.binge.companion.contracts.v1.PlaybackSource
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.info
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.server.DeviceProfiles
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.PLAY_METHOD_TRANSCODE
import com.cooper.homelab.ephyra.server.TICKS_PER_MILLI
import com.cooper.homelab.ephyra.server.array
import com.cooper.homelab.ephyra.server.directStreamUrl
import com.cooper.homelab.ephyra.server.externalSubtitles
import com.cooper.homelab.ephyra.server.item
import com.cooper.homelab.ephyra.server.long
import com.cooper.homelab.ephyra.server.playMethodOf
import com.cooper.homelab.ephyra.server.playReportBody
import com.cooper.homelab.ephyra.server.playbackInfo
import com.cooper.homelab.ephyra.server.playbackInfoBody
import com.cooper.homelab.ephyra.server.reportProgress
import com.cooper.homelab.ephyra.server.reportStart
import com.cooper.homelab.ephyra.server.reportStopped
import com.cooper.homelab.ephyra.server.string
import com.cooper.homelab.ephyra.server.withApiKey
import com.cooper.homelab.ephyra.server.withoutApiKey
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.serialization.json.JsonObject
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
    private val logger: Logger,
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
        val source = info.array("MediaSources").firstOrNull() as? JsonObject ?: throw notFound()
        val sitting =
            Sitting(
                itemId = item.id,
                mediaSourceId = source.string("Id") ?: item.id,
                playSessionId = info.string("PlaySessionId").orEmpty(),
                playMethod = playMethodOf(source),
            )
        val stream = stream(client, source, sitting, tokenInUrl = if (request.playerPackage.isNotEmpty()) session.token else null)
        val runtimeMillis = (source.long("RunTimeTicks") ?: item.runTimeTicks ?: 0L) / TICKS_PER_MILLI
        val wantsTrickplay = wantsTrickplay(session, request)
        val fresh = freshItem(client, session, item, withTrickplay = wantsTrickplay)
        val resumeMillis = fresh.userData?.takeUnless { it.played }?.positionTicks ?: 0L
        val trickplay = trickplay(client, fresh, sitting, wantsTrickplay)
        logger.info(TAG, "playback source minted", listOf(LogField.Text("method", sitting.playMethod.toString())))
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
                    PLAY_METHOD_TRANSCODE
                ) {
                    PlaybackMethod.PLAYBACK_METHOD_TRANSCODE
                } else {
                    PlaybackMethod.PLAYBACK_METHOD_DIRECT_PLAY
                },
            ).addAllSubtitles(subtitles(client, source, session.token))
            .apply { trickplay?.let { setTrickplay(it) } }
            .build()
    }

    /**
     * Seek thumbnails are for Binge's own player alone, and only on a server that makes them: an installed player cannot send
     * the headers the sheets need, and the token never goes into a template to make up for it.
     */
    private fun wantsTrickplay(
        session: JellyfinSession,
        request: GetPlaybackSourceRequest,
    ): Boolean = request.playerPackage.isEmpty() && JellyfinServerProfile.from(session.serverVersion).hasTrickplay

    /** The item as the server has it now, for the resume point, and with its trickplay sets when [withTrickplay]. */
    private suspend fun freshItem(
        client: JellyfinClient,
        session: JellyfinSession,
        item: JellyfinItem,
        withTrickplay: Boolean,
    ): JellyfinItem = client.item(session.userId, item.id, fields = if (withTrickplay) "ProviderIds,Trickplay" else "ProviderIds") ?: item

    /** The descriptor for the source being played, or null: not wanted, none held for that source, or one the contract would drop. */
    private fun trickplay(
        client: JellyfinClient,
        item: JellyfinItem,
        sitting: Sitting,
        wanted: Boolean,
    ): Trickplay? =
        item.trickplay[sitting.mediaSourceId]
            ?.takeIf { wanted }
            ?.let { TrickplayDescriptors.descriptor(client, sitting.mediaSourceId, it) }

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

    /** The server's choice: a direct stream of the file when it can play as it is, else its HLS transcode. */
    private fun stream(
        client: JellyfinClient,
        source: JsonObject,
        sitting: Sitting,
        tokenInUrl: String?,
    ): PlaybackSource {
        val transcodingUrl = source.string("TranscodingUrl")
        if (sitting.playMethod == PLAY_METHOD_TRANSCODE && transcodingUrl != null) {
            return PlaybackSource
                .newBuilder()
                .setUrl(client.resolve(transcodingUrl).withoutApiKey().withApiKey(tokenInUrl))
                .setMimeType(HLS)
                .build()
        }
        return PlaybackSource
            .newBuilder()
            .setUrl(client.directStreamUrl(sitting.itemId, sitting.mediaSourceId, sitting.playSessionId, tokenInUrl))
            .setMimeType(DeviceProfiles.mimeForContainer(source.string("Container")))
            .build()
    }

    /** The server's external text subtitles, each carrying the token: the host hands a subtitle URL to the player without headers. */
    private fun subtitles(
        client: JellyfinClient,
        source: JsonObject,
        token: String,
    ): List<SubtitleTrack> =
        source.externalSubtitles().map {
            SubtitleTrack
                .newBuilder()
                .setUrl(client.resolve(it.path).withApiKey(token))
                .setLanguage(it.language)
                .setLabel(it.label.orEmpty())
                .setMimeType(it.mimeType)
                .setForced(it.forced)
                .setHearingImpaired(it.hearingImpaired)
                .build()
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
        ): JsonObject = playReportBody(itemId, mediaSourceId, playSessionId, playMethod, ticks, paused)

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
        const val HLS = "application/x-mpegURL"
        const val SOURCE_LIFETIME_SECONDS = 6 * 60 * 60L
    }
}
