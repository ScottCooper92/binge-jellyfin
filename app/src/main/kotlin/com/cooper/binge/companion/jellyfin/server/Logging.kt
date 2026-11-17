package com.cooper.binge.companion.jellyfin.server

import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.warn

/**
 * A warning that something went wrong and the app went on: [event] is a constant sentence and [error] names its class only. The
 * message of an exception can hold an address or a token, so none is ever put in the event.
 */
fun Logger.noted(
    tag: String,
    event: String,
    error: Throwable? = null,
) = warn(tag, event, error = error)
