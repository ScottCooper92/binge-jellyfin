package com.cooper.binge.companion.jellyfin.notifications

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.auth.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Keeps the poll's schedule in step with the session, the switch and the interval: scheduled while someone is signed in, the
 * switch is on and the last poll's sign-in was not refused; cancelled otherwise. Signing out clears the notices, which describe
 * a server the user has left. A new sign-in clears a pause, so a user who signs in again after a refusal is polled again.
 */
class NotificationPlanner(
    private val sessions: SessionStore,
    private val prefs: NotificationPrefs,
    private val scheduler: NotificationScheduler,
    private val notifier: ActivityNotifier,
    private val scope: CoroutineScope,
) {
    fun start() {
        sessions.session
            .distinctUntilChanged()
            // The first value is the session the app started with; only a sign-in made while running un-pauses.
            .drop(1)
            .onEach { session -> if (session != null) prefs.setPausedForAuthFailure(false) }
            .launchIn(scope)
        combine(sessions.session, prefs.enabled, prefs.pausedForAuthFailure, prefs.intervalMinutes) { session, enabled, paused, interval ->
            Plan(session, enabled, paused, interval)
        }.distinctUntilChanged()
            .onEach(::apply)
            .launchIn(scope)
    }

    private fun apply(plan: Plan) {
        scope.launch {
            if (plan.session == null) notifier.cancelActivity()
            if (!plan.paused) notifier.cancelConnectionProblem()
        }
        val wanted = plan.session != null && plan.enabled && !plan.paused
        // WorkManager can fail to start on a device; that must not take the app with it. Notifications are off there.
        attempt { if (wanted) scheduler.schedule(plan.interval) else scheduler.cancel() }
    }

    private data class Plan(
        val session: JellyfinSession?,
        val enabled: Boolean,
        val paused: Boolean,
        val interval: Int,
    )
}
