package com.cooper.binge.companion.jellyfin.server

import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.Logger

/** A [Logger] that keeps nothing: the default for a client or connection a test builds, which does not look at the log. */
val DiscardLogger: Logger = Logger { _, _, _, _, _ -> }

/**
 * A note that something went wrong and the app went on: [event] is a constant sentence and [error] names its class only. The
 * message of an exception can hold an address or a token, so none is ever put in the event.
 */
fun Logger.noted(
    tag: String,
    event: String,
    error: Throwable? = null,
) = log(LogLevel.Info, tag, event, emptyList(), error)
