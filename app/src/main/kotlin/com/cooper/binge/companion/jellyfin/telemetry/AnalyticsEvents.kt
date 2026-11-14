package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.ui.SetupError

/**
 * The events this app reports, by fixed name, and the only property names and values they carry. Each is a category: never an
 * id, a title, an address, a user name or anything read from the server. Reported only after the user has agreed (see
 * [PostHogAnalytics]).
 */
object AnalyticsEvents {
    /** A sign-in succeeded. Property: [METHOD]. */
    const val SIGN_IN = "sign_in"

    /** A sign-in failed. Properties: [METHOD] and [REASON]. */
    const val SIGN_IN_FAILED = "sign_in_failed"

    /** The user signed out. */
    const val SIGN_OUT = "sign_out"

    const val METHOD = "method"
    const val REASON = "reason"

    const val METHOD_PASSWORD = "password"
    const val METHOD_QUICK_CONNECT = "quick_connect"
    const val METHOD_DEMO = "demo"
}

/** A failed sign-in as a fixed reason name, so no message or address ever goes with it. */
internal val SetupError.analyticsReason: String
    get() =
        when (this) {
            SetupError.WrongCredentials -> "wrong_credentials"
            SetupError.Unreachable -> "unreachable"
            SetupError.LocalNetworkDenied -> "local_network_denied"
            SetupError.NotJellyfin -> "not_jellyfin"
            SetupError.QuickConnectOff -> "quick_connect_off"
            SetupError.QuickConnectExpired -> "quick_connect_expired"
            SetupError.InvalidAddress, SetupError.Generic -> "other"
        }
