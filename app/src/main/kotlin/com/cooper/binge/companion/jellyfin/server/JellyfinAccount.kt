package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject

/**
 * What the signed-in user may do, from their own policy (`/Users/Me`'s `Policy`). A page asks for a
 * named permission, never for a policy field. An administrator may do everything. A policy that
 * hasn't been read yet allows only what every user has, so nothing an admin alone may do flashes up.
 */
data class JellyfinAccount(
    val name: String = "",
    private val policy: JsonObject = JsonObject(emptyMap()),
) {
    /** Whether the policy has been read: until then, a page this account can't open may still be one it can. */
    val isRead: Boolean get() = policy.isNotEmpty()

    val isAdministrator: Boolean get() = policy.bool("IsAdministrator") == true

    /** Changing their own preferences: languages, playback, the home screen. On unless the admin turned it off. */
    val canEditPreferences: Boolean get() = isAdministrator || policy.bool("EnableUserPreferenceAccess") != false

    /**
     * Changing their own password here: a user signed in through another provider (LDAP, say) has no
     * password for Jellyfin to change.
     */
    val hasLocalPassword: Boolean
        get() = policy.string("AuthenticationProviderId")?.let { it == DEFAULT_PROVIDER } ?: true

    /** Stopping a stream on someone else's session, not only their own. */
    val canControlOthers: Boolean get() = isAdministrator || policy.bool("EnableRemoteControlOfOtherUsers") == true

    /**
     * Controlling a session on a shared device (one nobody is signed in on). Off unless the policy says so: the server
     * reads this flag on the controlling user and gives an administrator no shortcut, so neither does this.
     */
    val canControlSharedDevices: Boolean get() = policy.bool("EnableSharedDeviceControl") == true

    /**
     * Changing a server's Live TV tuners and guide data. The server reads this flag on the user and gives an administrator no
     * shortcut, so neither does this: an administrator with it off is refused.
     */
    val canManageLiveTv: Boolean get() = policy.bool(POLICY_LIVE_TV_MANAGEMENT) == true

    /** Live TV itself. The server's lookups for the add forms (tuner types, lineups, countries) need this as well as [canManageLiveTv]. */
    val canAccessLiveTv: Boolean get() = policy.bool(POLICY_LIVE_TV_ACCESS) == true

    private companion object {
        const val DEFAULT_PROVIDER = "Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider"
    }
}
