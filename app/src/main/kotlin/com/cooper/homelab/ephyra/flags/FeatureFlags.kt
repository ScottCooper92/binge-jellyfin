package com.cooper.homelab.ephyra.flags

import androidx.compose.runtime.staticCompositionLocalOf
import com.cooper.homelab.core.flags.FlagKey
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags

/** The flags for the composition below. Off until a root provides them, so a screen shown alone names no Binge. */
val LocalFlags = staticCompositionLocalOf { Flags() }

/** Anything that asks the user to install, open or connect Binge. */
val Flags.bingePromotion: Boolean get() = this[SharedFlags.BingePromotion]

/** Whether the client surfaces show (browsing and playing the user's library). Off until a root provides it, so a screen shown alone is today's app. */
val LocalClientEnabled = staticCompositionLocalOf { false }

/**
 * Whether the app is a client for the user's server as well as its console (epic #759). Off until Remote Config turns it on, and
 * off in a build with no Firebase app. A build made with `-PclientEnabled=true` turns it on whatever the remote says. With it
 * off the app is exactly as it was before the epic.
 */
val ClientEnabled = FlagKey("client_enabled")

/** The flags Remote Config is asked for: the shared ones, and this app's client flag. */
val RemoteFlagKeys: List<FlagKey> = SharedFlags.all + ClientEnabled
