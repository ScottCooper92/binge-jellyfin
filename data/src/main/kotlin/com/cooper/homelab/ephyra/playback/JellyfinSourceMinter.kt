package com.cooper.homelab.ephyra.playback

import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.browse.browseTitle
import com.cooper.homelab.ephyra.server.DeviceProfiles
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinItem
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.MediaSegment
import com.cooper.homelab.ephyra.server.PLAY_METHOD_DIRECT_PLAY
import com.cooper.homelab.ephyra.server.PLAY_METHOD_TRANSCODE
import com.cooper.homelab.ephyra.server.SegmentKind
import com.cooper.homelab.ephyra.server.TICKS_PER_MILLI
import com.cooper.homelab.ephyra.server.array
import com.cooper.homelab.ephyra.server.bool
import com.cooper.homelab.ephyra.server.directStreamUrl
import com.cooper.homelab.ephyra.server.externalSubtitles
import com.cooper.homelab.ephyra.server.item
import com.cooper.homelab.ephyra.server.long
import com.cooper.homelab.ephyra.server.mediaSegments
import com.cooper.homelab.ephyra.server.playMethodOf
import com.cooper.homelab.ephyra.server.playbackInfo
import com.cooper.homelab.ephyra.server.playbackInfoBody
import com.cooper.homelab.ephyra.server.string
import com.cooper.homelab.ephyra.server.trickplayDescriptor
import com.cooper.homelab.ephyra.server.withoutApiKey
import com.cooper.homelab.player.api.PlayChapter
import com.cooper.homelab.player.api.PlayMethod
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlaySegment
import com.cooper.homelab.player.api.PlaySegmentKind
import com.cooper.homelab.player.api.PlaySource
import com.cooper.homelab.player.api.PlayTrack
import com.cooper.homelab.player.api.PlayTrickplay
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import kotlinx.serialization.json.JsonObject

private const val HLS = "application/x-mpegURL"
private const val NOT_FOUND = 404
private const val FORBIDDEN = 403
private const val REFUSED = 409

/**
 * Asks the server for a source the player can play. `POST /Items/{itemId}/PlaybackInfo` carries the player's device profile as a
 * Jellyfin `DeviceProfile`, and the server answers with a media source: played as it is when the device can
 * (`GET /Videos/{id}/stream?static=true`), or as the server's own HLS transcode (`TranscodingUrl`) when it cannot. This app never
 * inspects the file.
 *
 * The user's own cap on the bitrate ([cap], read at each mint) lowers the profile's ceiling before the server sees it, so the body and the
 * profile carry the same number; a cap never raises what the device says it can play.
 *
 * The token is in the `Authorization` header the source carries and in no URL: the player sends that header on every request it makes,
 * the subtitle files included, so a subtitle needs no `ApiKey` of its own.
 */
class JellyfinSourceMinter(
    private val client: JellyfinClient,
    private val userId: String,
    private val server: JellyfinServerProfile,
    private val cap: StreamingCap = NoStreamingCap,
) : SourceMinter {
    override suspend fun mint(
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        resumeMs: Long?,
        choice: StreamChoice?,
    ): Minted {
        val read = attempt { client.item(userId, item.itemId, ITEM_FIELDS) }
        val resume = resumeMs ?: savedPosition(read.getOrThrow())
        val capped = cap.maxBitrateBps()?.let(profile::cappedTo) ?: profile
        val body = playbackInfoBody(userId, capped.toContract())
        val first = client.playbackInfo(userId, item.itemId, body, resume * TICKS_PER_MILLI)
        val info = choice?.let { withChoice(item, body, resume, first, it) } ?: first
        info.string("ErrorCode")?.let { throw JellyfinHttpException(if (it == "NotAllowed") FORBIDDEN else REFUSED, PLAYBACK_INFO) }
        val source = info.array("MediaSources").firstOrNull() as? JsonObject ?: throw JellyfinHttpException(NOT_FOUND, PLAYBACK_INFO)
        val method = playMethodOf(source)
        val mediaSourceId = source.string("Id") ?: item.itemId
        val playSessionId = info.string("PlaySessionId").orEmpty()
        val runtimeMs = (source.long("RunTimeTicks") ?: 0L) / TICKS_PER_MILLI
        val defaults = if (choice == null) source.serverDefaults() else null
        val request =
            PlayRequest(
                sessionId = "",
                source = playSource(source, item.itemId, mediaSourceId, playSessionId, method),
                title = item.title,
                resumePositionMs = resume,
                runtimeMs = runtimeMs,
                subtitles = subtitles(source, method),
                chapters = chapters(item.itemId),
                segments = segments(item.itemId, source),
                trickplay = trickplay(read.getOrNull(), mediaSourceId),
                audioOptions = source.audioOptions(),
                preferredAudio = defaults?.audio,
                preferredSubtitle = defaults?.subtitle,
            )
        return Minted(request, JellyfinPlayHandle(item.itemId, mediaSourceId, playSessionId, method, runtimeMs))
    }

    /**
     * Asks again, naming the streams of [choice], when the server's first answer is on others: the server picks the tracks of a transcode, so
     * the choice has to be in the request. Null, so the first answer stands, when it already matches, the choice names nothing this source
     * has, the server refused, the only difference is subtitles the server burns in, or the second ask fails or has no playable source: a
     * track hint never stops a play.
     */
    private suspend fun withChoice(
        item: PlayItem,
        body: JsonObject,
        resume: Long,
        first: JsonObject,
        choice: StreamChoice,
    ): JsonObject? {
        if (first.string("ErrorCode") != null) return null
        val source = first.array("MediaSources").firstOrNull() as? JsonObject ?: return null
        val wanted = source.streamIndexes(choice)
        val chosen = source.chosenIndexes()
        val audio = wanted.audio?.takeIf { it != chosen.audio }
        val subtitle = wanted.subtitle?.takeIf { it != chosen.subtitle && !(it == SUBTITLES_OFF && source.burnsInSubtitle()) }
        if (audio == null && subtitle == null) return null
        val streams =
            buildMap {
                source.string("Id")?.let { put("mediaSourceId", it) }
                audio?.let { put("audioStreamIndex", it.toString()) }
                subtitle?.let { put("subtitleStreamIndex", it.toString()) }
            }
        return attempt { client.playbackInfo(userId, item.itemId, body, resume * TICKS_PER_MILLI, streams) }
            .getOrNull()
            ?.takeIf { it.string("ErrorCode") == null && it.array("MediaSources").firstOrNull() is JsonObject }
    }

    /** The item's chapters, as the page reads them; none when the read fails, since a play does not need them. */
    private suspend fun chapters(itemId: String): List<PlayChapter> =
        attempt { client.browseTitle(userId, server, itemId)?.chapters }
            .getOrNull()
            .orEmpty()
            .map { PlayChapter(it.startTicks / TICKS_PER_MILLI, it.name) }

    /** What the server has marked to skip, where it has any: an older server has none, and a source says when it holds none. */
    private suspend fun segments(
        itemId: String,
        source: JsonObject,
    ): List<PlaySegment> =
        if (!server.hasMediaSegments || source.bool("HasSegments") == false) {
            emptyList()
        } else {
            attempt { client.mediaSegments(itemId) }.getOrNull().orEmpty().mapNotNull { it.toPlaySegment() }
        }

    /** Where the server has the user in [item]: its resume point, or the start once it is played or the server does not know it. */
    private fun savedPosition(item: JellyfinItem?): Long = (item?.userData?.takeUnless { it.played }?.positionTicks ?: 0L) / TICKS_PER_MILLI

    /** The seek-thumbnail sheets the server holds for [mediaSourceId], where it makes them; none for a read that failed. */
    private fun trickplay(
        item: JellyfinItem?,
        mediaSourceId: String,
    ): PlayTrickplay? {
        val found =
            item
                ?.takeIf { server.hasTrickplay }
                ?.trickplay
                ?.get(mediaSourceId)
                .orEmpty()
        val descriptor = client.trickplayDescriptor(mediaSourceId, found) ?: return null
        return PlayTrickplay(
            urlTemplate = descriptor.urlTemplate,
            thumbnailWidth = descriptor.thumbnailWidth,
            thumbnailHeight = descriptor.thumbnailHeight,
            tileColumns = descriptor.tileColumns,
            tileRows = descriptor.tileRows,
            intervalMs = descriptor.intervalMillis,
            thumbnailCount = descriptor.thumbnailCount,
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
        if (method == PLAY_METHOD_TRANSCODE && transcodingUrl != null) {
            return PlaySource(client.resolve(transcodingUrl).withoutApiKey(), HLS, headers, method = PlayMethod.TRANSCODE)
        }
        val mime = DeviceProfiles.mimeForContainer(source.string("Container"))
        return PlaySource(client.directStreamUrl(itemId, mediaSourceId, playSessionId), mime, headers, method = PlayMethod.DIRECT_PLAY)
    }

    /**
     * The server's external text subtitles, which the player loads beside the source. Embedded ones stay in the stream. On a direct play
     * the player reads the file's own text tracks, so those are not loaded a second time; a transcode has them nowhere else, and a
     * subtitle file beside the video is always loaded.
     */
    private fun subtitles(
        source: JsonObject,
        method: String,
    ): List<PlayTrack> =
        source.externalSubtitles(playerReadsFile = method == PLAY_METHOD_DIRECT_PLAY).map {
            PlayTrack(
                url = client.resolve(it.path).withoutApiKey(),
                language = it.language,
                label = it.label,
                mimeType = it.mimeType,
                forced = it.forced,
                hearingImpaired = it.hearingImpaired,
            )
        }

    private fun MediaSegment.toPlaySegment(): PlaySegment? {
        val playKind =
            when (kind) {
                SegmentKind.Intro -> PlaySegmentKind.INTRO
                SegmentKind.Recap -> PlaySegmentKind.RECAP
                SegmentKind.Preview -> PlaySegmentKind.PREVIEW
                SegmentKind.Outro -> PlaySegmentKind.CREDITS
                SegmentKind.Other -> return null
            }
        return PlaySegment(playKind, startMillis, endMillis)
    }

    private companion object {
        const val PLAYBACK_INFO = "Items/{id}/PlaybackInfo"
        const val ITEM_FIELDS = "ProviderIds,Trickplay"
    }
}
