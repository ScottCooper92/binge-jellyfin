package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.PLAY_METHOD_TRANSCODE
import com.cooper.homelab.ephyra.server.TICKS_PER_MILLI
import com.cooper.homelab.ephyra.server.playReportBody
import com.cooper.homelab.ephyra.server.reportProgress
import com.cooper.homelab.ephyra.server.reportStart
import com.cooper.homelab.ephyra.server.reportStopped
import com.cooper.homelab.ephyra.server.stopEncoding
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What a play's reports name to the server: the item, the media source, the play session and the play method, all from the answer
 * to the call that minted the source. [runtimeMs] is the end of the item, where a finished play is reported.
 */
internal class JellyfinPlayHandle(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val playMethod: String,
    val runtimeMs: Long,
    /** The media source this play was minted from, whose streams a choice of tracks is mapped onto; none for a handle made without one. */
    private val source: JsonObject? = null,
) : PlayReportHandle {
    private val finished = AtomicBoolean(false)

    /** Whether the play has been reported finished. */
    val isFinished: Boolean get() = finished.get()

    /** Marks the play finished, once the server has been told. */
    fun finish() = finished.set(true)

    /** The streams playing for [choice], as the server numbers them; null without a media source to map them onto. */
    fun streams(choice: StreamChoice?): StreamIndexes? = source?.playingIndexes(choice)

    /** The report body at [positionMs], naming the [streams] playing where it is given. */
    fun body(
        positionMs: Long,
        paused: Boolean,
        streams: StreamIndexes? = null,
    ): JsonObject {
        val ticks = positionMs.coerceAtLeast(0L) * TICKS_PER_MILLI
        return playReportBody(itemId, mediaSourceId, playSessionId, playMethod, ticks, paused, streams?.audio, streams?.subtitle)
    }
}

/**
 * Reports a play to the server as a Jellyfin session: `POST /Sessions/Playing` when it starts, `/Sessions/Playing/Progress` for
 * progress, a pause and a resume (with `IsPaused`), and `/Sessions/Playing/Stopped` when it ends. The first three name the audio and subtitle
 * streams playing (`AudioStreamIndex`, `SubtitleStreamIndex`, -1 for none), from the sitting's choice mapped onto the media source. A finished play is a stop at the
 * end of the runtime, so the server marks the item played past its own threshold; a source that is replaced ends the transcode the server
 * made for it (`DELETE /Videos/ActiveEncodings`), if it was one; once it is finished the play is over for the
 * server, and the progress and stop that follow are not sent, so they cannot move the position back.
 */
class JellyfinPlayReporter(
    private val client: JellyfinClient,
) : PlayReporter {
    override suspend fun report(
        play: PlayReportHandle,
        kind: PlayReport,
        positionMs: Long,
        choice: StreamChoice?,
    ) {
        val handle = requireNotNull(play as? JellyfinPlayHandle) { "A play this reporter did not mint" }
        if (handle.isFinished) return
        val streams = handle.streams(choice)
        when (kind) {
            PlayReport.STARTED -> client.reportStart(handle.body(positionMs, paused = false, streams))
            PlayReport.PROGRESS, PlayReport.RESUMED -> client.reportProgress(handle.body(positionMs, paused = false, streams))
            PlayReport.PAUSED -> client.reportProgress(handle.body(positionMs, paused = true, streams))
            PlayReport.STOPPED -> client.reportStopped(handle.body(positionMs, paused = false))
            PlayReport.REPLACED -> if (handle.playMethod == PLAY_METHOD_TRANSCODE) client.stopEncoding(handle.playSessionId)
            PlayReport.FINISHED -> {
                client.reportStopped(handle.body(handle.runtimeMs.takeIf { it > 0 } ?: positionMs, paused = false))
                handle.finish()
            }
        }
    }
}
