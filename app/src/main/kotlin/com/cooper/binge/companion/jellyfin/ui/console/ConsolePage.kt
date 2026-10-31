package com.cooper.binge.companion.jellyfin.ui.console

/** A page of the console, opened from its home (the signed-in screen) and closed back to it. */
enum class ConsolePage {
    /** Approve a Quick Connect code another device shows. For everyone. */
    ApproveCode,

    /** The audio and subtitle languages Jellyfin chooses tracks by. For everyone. */
    Languages,

    /** Change the user's own password. For everyone. */
    Password,

    /** Who is watching what, and stopping a stream. Everyone's for an admin, else the user's own. */
    NowPlaying,
}
