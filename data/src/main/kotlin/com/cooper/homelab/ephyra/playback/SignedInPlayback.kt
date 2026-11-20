package com.cooper.homelab.ephyra.playback

import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.JellyfinHttpException
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.player.api.PlaybackDeviceProfile

private const val HTTP_UNAUTHORIZED = 401

/**
 * The saved session and a client signed in as it, read when each call is made: a play outlives the screen that began it, and a
 * session that has ended since answers like a token the server refused.
 */
private suspend fun Connection.signedInOrRefused() = signedIn() ?: throw JellyfinHttpException(HTTP_UNAUTHORIZED, "no session")

/** [JellyfinSourceMinter] for whichever session is saved when the source is asked for, held to the user's own [cap] on the bitrate. */
class SignedInSourceMinter(
    private val connection: Connection,
    private val cap: StreamingCap = NoStreamingCap,
) : SourceMinter {
    override suspend fun mint(
        item: PlayItem,
        profile: PlaybackDeviceProfile,
        resumeMs: Long?,
    ): Minted {
        val (session, client) = connection.signedInOrRefused()
        return JellyfinSourceMinter(
            client,
            session.userId,
            JellyfinServerProfile.from(session.serverVersion),
            cap,
        ).mint(item, profile, resumeMs)
    }
}

/** [JellyfinPlayReporter] for whichever session is saved when the report is made. */
class SignedInPlayReporter(
    private val connection: Connection,
) : PlayReporter {
    override suspend fun report(
        play: PlayReportHandle,
        kind: PlayReport,
        positionMs: Long,
    ) {
        val (_, client) = connection.signedInOrRefused()
        JellyfinPlayReporter(client).report(play, kind, positionMs)
    }
}

/** [JellyfinNextEpisodes] for whichever session is saved when the next episode is asked for. */
class SignedInNextEpisodes(
    private val connection: Connection,
) : NextEpisodes {
    override suspend fun after(item: PlayItem): PlayItem? {
        val (session, client) = connection.signedInOrRefused()
        return JellyfinNextEpisodes(client, session.userId).after(item)
    }
}
