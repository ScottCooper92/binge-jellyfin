package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile

/**
 * A page of the console, opened from its home (the signed-in screen) and closed back to it. Each
 * says what it needs (see [available]): the home lists only the pages this server and this user can
 * use, and a page that stops being available closes.
 */
enum class ConsolePage {
    /** Approve a Quick Connect code another device shows. For everyone. */
    ApproveCode,

    /** The audio and subtitle languages Jellyfin chooses tracks by. For everyone. */
    Languages,

    /** Change the user's own password. For everyone. */
    Password,

    /** Who is watching what, and stopping a stream. Everyone's for an admin, else the user's own. */
    NowPlaying,

    /** The server's libraries, and scanning them. Admin only. */
    Libraries,

    /** The server's scheduled tasks, run and stopped. Admin only. */
    Tasks,

    /** The server's activity log. Admin only. */
    Activity,

    /** The devices signed in to the server, renamed or removed. Admin only. */
    Devices,

    /** The server's users: create, access, password, delete. Admin only. */
    Users,

    /** The server itself: its info, and restarting it. Admin only. */
    Server,
    ;

    /**
     * Whether this server and this user can use the page: the version gate ([profile]), the account gate
     * ([account]), and what the server's administrator turned on. `docs/server-compatibility.md` lists them.
     */
    fun available(
        profile: JellyfinServerProfile,
        account: JellyfinAccount,
    ): Boolean =
        when (this) {
            ApproveCode -> profile.quickConnect
            Languages -> account.canEditPreferences
            Password -> account.hasLocalPassword
            NowPlaying -> true
            Libraries, Tasks, Activity, Devices, Users, Server -> account.isAdministrator
        }

    /** One of the administrator's pages, listed together on the home. */
    val isAdmin: Boolean get() = this in setOf(Libraries, Tasks, Activity, Devices, Users, Server)
}
