package com.cooper.binge.companion.jellyfin.telemetry

import com.cooper.binge.companion.jellyfin.ui.SetupError

/** The sign-in events, by [AnalyticsEvents]' fixed names, so the view model says what happened and not how it is reported. */
class SignInReports(
    private val analytics: Analytics,
) {
    fun signedIn(method: String) = analytics.event(AnalyticsEvents.SIGN_IN, mapOf(AnalyticsEvents.METHOD to method))

    fun failed(
        method: String,
        error: SetupError,
    ) = analytics.event(
        AnalyticsEvents.SIGN_IN_FAILED,
        mapOf(AnalyticsEvents.METHOD to method, AnalyticsEvents.REASON to error.analyticsReason),
    )

    fun signedOut() = analytics.event(AnalyticsEvents.SIGN_OUT)
}
