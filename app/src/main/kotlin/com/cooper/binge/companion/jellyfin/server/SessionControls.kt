package com.cooper.binge.companion.jellyfin.server

/**
 * What the signed-in user may do to one session, from the gates that all have to agree. The server's send endpoints
 * check nothing, so none of this is ever left to a refusal. A control is offered only when it is [available] for the
 * session, and a session this app itself holds is never controllable.
 */
data class SessionControls(
    val playPause: Boolean = false,
    val seek: Boolean = false,
    val skip: Boolean = false,
    val stop: Boolean = false,
) {
    /** Whether anything at all is offered. */
    val any: Boolean get() = playPause || seek || skip || stop

    companion object {
        /**
         * The controls for [session]. [controllable] is the ids from `GET /Sessions?controllableByUserId=`; [ownDeviceId] is
         * this install's, whose session is never controlled; [account] is the signed-in user's policy and [ownUserId] their id.
         */
        fun of(
            session: NowPlaying,
            controllable: Set<String>,
            account: JellyfinAccount,
            ownUserId: String,
            ownDeviceId: String,
        ): SessionControls {
            if (session.sessionId !in controllable) return SessionControls()
            if (session.deviceId == ownDeviceId) return SessionControls()
            if (!session.supportsRemoteControl) return SessionControls()
            val allowed =
                when {
                    session.userId == null -> account.canControlSharedDevices
                    session.userId.sameUser(ownUserId) -> true
                    else -> account.canControlOthers
                }
            if (!allowed) return SessionControls()
            return SessionControls(
                playPause = session.supportsMediaControl,
                seek = session.supportsMediaControl && session.canSeek,
                skip = session.supportsMediaControl,
                stop = session.supportsMediaControl,
            )
        }
    }
}

/** Jellyfin writes a user id with dashes in some answers and without in others. */
private fun String.sameUser(other: String): Boolean = normalisedId() == other.normalisedId()

private fun String.normalisedId(): String = lowercase().replace("-", "")
