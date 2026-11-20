package com.cooper.homelab.ephyra.playback

import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.browse.browseTitle
import com.cooper.homelab.ephyra.server.DeviceProfiles
import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.MediaSegment
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
import com.cooper.homelab.ephyra.server.withoutApiKey
import com.cooper.homelab.player.api.PlayChapter
import com.cooper.homelab.player.api.PlayMethod
import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlaySegment
import com.cooper.homelab.player.api.PlaySegmentKind
import com.cooper.homelab.player.api.PlaySource
import com.cooper.homelab.player.api.PlayTrack
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
 * The token is in the `Authorization` header the source carries and in no URL: the player sends that header on every request it makes,
 * the subtitle files included, so a subtitle needs no `ApiKey` of its own.
 */
class JellyfinSourceMinter(
    private val client: JellyfinClient,
    private val userId: String,
    private val server: JellyfinServerProfile,
) : SourceMinter {
    override suspend fun mint(
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        resumeMs: Long?,
    ): Minted {
        val resume = resumeMs ?: savedPosition(item.itemId)
        val info = client.playbackInfo(userId, item.itemId, playbackInfoBody(userId, profile.toContract()), resume * TICKS_PER_MILLI)
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
                chapters = chapters(item.itemId),
                segments = segments(item.itemId, source),
            )
        return Minted(request, JellyfinPlayHandle(item.itemId, mediaSourceId, playSessionId, method, runtimeMs))
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

    /** Where the server has the user in the item: its resume point, or the start once the item is played. */
    private suspend fun savedPosition(itemId: String): Long =
        (
            client
                .item(userId, itemId)
                ?.userData
                ?.takeUnless { it.played }
                ?.positionTicks ?: 0L
        ) / TICKS_PER_MILLI

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

    /** The server's external text subtitles, which the player loads beside the source. Embedded ones stay in the stream. */
    private fun subtitles(source: JsonObject): List<PlayTrack> =
        source.externalSubtitles().map {
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
    }
}
