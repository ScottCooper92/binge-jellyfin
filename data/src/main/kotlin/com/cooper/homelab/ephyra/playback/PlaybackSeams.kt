package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.player.api.PlayRequest
import com.cooper.homelab.player.api.PlayTitle
import com.cooper.homelab.player.api.PlaybackDeviceProfile
import com.cooper.homelab.player.api.TrackChoice

/** One thing to play: the server's id for it, and how the player names it. */
data class PlayItem(
    val itemId: String,
    val title: PlayTitle,
)

/** What a play's reports are made against: the item, the media source, the play session and the play method. The sitting never opens it. */
interface PlayReportHandle

/** A source the server minted for one item. The [request]'s session id is the sitting's own, set by the sitting. */
class Minted(
    val request: PlayRequest,
    val report: PlayReportHandle,
)

/**
 * The audio and subtitle tracks a sitting is on, as the player last said (and, for a transcode, as the user picked them from the host's list): by language, and by label where the language is not enough.
 * A null [subtitle] is subtitles off. Kept for the sitting only; it is never written to the account's own preferences.
 */
data class StreamChoice(
    val audio: TrackChoice?,
    val subtitle: TrackChoice?,
    /** The exact audio stream the user picked from the host's own list, for the item being played; it wins over [audio] for that item. */
    val audioStream: Int? = null,
)

/**
 * Mints a source for [item] that a device with [profile] can play, from [resumeMs], or from the server's own resume point where it is null.
 * With a [choice], the source carries the tracks it names where the item has them; with none, the server's own defaults decide.
 */
fun interface SourceMinter {
    suspend fun mint(
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        resumeMs: Long?,
        choice: StreamChoice?,
    ): Minted
}

/** Why a switch of audio in a play did not happen: the server could not be reached, or it would not make the source. */
enum class AudioSwitchFailure { Unreachable, Refused }

enum class PlayReport { STARTED, PROGRESS, PAUSED, RESUMED, FINISHED, STOPPED }

/** Tells the server where a play stands. A failure here never ends the play. */
fun interface PlayReporter {
    suspend fun report(
        play: PlayReportHandle,
        kind: PlayReport,
        positionMs: Long,
    )
}

/** The item that follows [item] in a series, or null when nothing does. */
fun interface NextEpisodes {
    suspend fun after(item: PlayItem): PlayItem?
}
