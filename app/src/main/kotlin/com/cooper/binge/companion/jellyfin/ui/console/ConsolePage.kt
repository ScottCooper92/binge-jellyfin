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

    /** The audio and subtitle languages Jellyfin chooses tracks by, and the playback switches. For everyone. */
    Playback,

    /** How Jellyfin's apps lay out the user's home: library order, where each shows. For everyone. */
    HomeLayout,

    /** Change the user's own password. For everyone. */
    Password,

    /**
     * Set, change and remove the user's own picture. For everyone whose policy lets them edit their own preferences: the server's
     * image routes refuse a user without `EnableUserPreferenceAccess` (an administrator always may).
     */
    ProfilePicture,

    /** An administrator's own settings, behind the avatar: the pages everyone else has inline on their home. */
    Account,

    /** Who is watching what, and stopping a stream. Everyone's for an admin, else the user's own. */
    NowPlaying,

    /** The server's libraries, and scanning them. Admin only. */
    Libraries,

    /** The server's scheduled tasks, run and stopped. Admin only. */
    Tasks,

    /** The server's activity log. Admin only. */
    Activity,

    /** The server's log files: read, search and share. Admin only. */
    Logs,

    /** The server's API keys: listed, created and revoked. Admin only. */
    ApiKeys,

    /** The devices signed in to the server, renamed or removed. Admin only. */
    Devices,

    /** The server's users: create, access, password, delete. Admin only. */
    Users,

    /** The server's general settings and login branding. Admin only. */
    ServerSettings,

    /** The server's plugins: installed, the catalog, and the repositories it fetches from. Admin only. */
    Plugins,

    /** The server's trickplay (seek thumbnail) options. Admin only, on a server that has trickplay. */
    Trickplay,

    /** The server's network options: ports, HTTPS, addresses, remote access. Admin only. */
    Network,

    /** The server's advanced options: scanning, caches, log retention, CORS. Admin only. */
    Advanced,

    /** The server's library display and metadata defaults. Admin only. */
    LibraryDisplay,

    /** The server's backups: made, and restored from. Admin only, on a server with backups. */
    Backups,

    /** The server's transcoding, resume and streaming settings. Admin only. */
    Transcoding,

    /** The server's Live TV tuners and guide data. For a user whose policy lets them manage Live TV, an administrator or not. */
    LiveTv,

    /** How the server records Live TV: folders, padding and saved files. Opened from the Live TV page. Admin only. */
    LiveTvRecording,

    /** The server itself: its info, restarting it and shutting it down. Admin only. */
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
            Playback, HomeLayout, ProfilePicture -> account.canEditPreferences
            Password -> account.hasLocalPassword
            NowPlaying -> true
            Trickplay -> account.isAdministrator && profile.hasTrickplay
            Plugins -> account.isAdministrator
            LiveTv -> account.canManageLiveTv
            LiveTvRecording -> account.isAdministrator && account.canManageLiveTv
            Backups -> account.isAdministrator && profile.hasBackups
            Account, Libraries, Tasks, Activity, Logs, ApiKeys, Devices, Users, Advanced,
            ServerSettings, Network, LibraryDisplay, Transcoding, Server,
            -> account.isAdministrator
        }

    /** One of the administrator's pages, listed together on the home. */
    val isAdmin: Boolean get() =
        this in
            setOf(
                Libraries,
                Tasks,
                Activity,
                Logs,
                ApiKeys,
                Devices,
                Users,
                ServerSettings,
                Network,
                Advanced,
                LibraryDisplay,
                Transcoding,
                Trickplay,
                Plugins,
                Backups,
                Server,
            )
}
