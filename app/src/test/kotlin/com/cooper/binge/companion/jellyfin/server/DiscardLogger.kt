package com.cooper.binge.companion.jellyfin.server

import com.cooper.homelab.core.foundation.Logger

/** A [Logger] that keeps nothing: for a test that builds a client or connection and does not look at the log. */
val DiscardLogger: Logger = Logger { _, _, _, _, _ -> }
