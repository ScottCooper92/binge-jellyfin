package com.cooper.homelab.ephyra.notifications

import com.cooper.homelab.core.notifications.PollNotifier
import com.cooper.homelab.core.notifications.PollPlan
import com.cooper.homelab.core.notifications.PollPlanner
import com.cooper.homelab.core.notifications.PollScheduler
import com.cooper.homelab.core.notifications.ServerPollPlan
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.auth.ServerIdentity
import com.cooper.homelab.ephyra.auth.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Keeps the poll's schedule in step with the session, the switch and the interval, through the shared [PollPlanner]: scheduled
 * while someone is signed in, the switch is on and the last poll's sign-in was not refused; cancelled otherwise. Signing out
 * clears the notices, which describe a server the user has left. A new sign-in clears a pause, so a user who signs in again after
 * a refusal is polled again.
 */
class NotificationPlanner(
    private val sessions: SessionStore,
    private val prefs: NotificationPrefs,
    private val serverIdentity: ServerIdentity,
    scheduler: PollScheduler,
    notifier: PollNotifier,
    private val scope: CoroutineScope,
    breadcrumbs: CrashBreadcrumbs,
) {
    private val planner = PollPlanner(plans(), scheduler, notifier, scope, breadcrumbs)

    fun start() {
        sessions.session
            .distinctUntilChanged()
            // The first value is the session the app started with; only a sign-in made while running un-pauses.
            .drop(1)
            .onEach { session -> if (session != null) prefs.setPausedForAuthFailure(false) }
            .launchIn(scope)
        planner.start()
    }

    private fun plans(): Flow<PollPlan> =
        combine(sessions.session, prefs.enabled, prefs.pausedForAuthFailure, prefs.intervalMinutes) { session, enabled, paused, interval ->
            Plan(session, enabled, paused, interval)
        }.distinctUntilChanged()
            .map { plan ->
                val server = serverIdentity.currentServerId()
                PollPlan(listOf(ServerPollPlan(server, plan.session != null, plan.enabled, plan.paused, plan.session)), plan.interval)
            }

    private data class Plan(
        val session: JellyfinSession?,
        val enabled: Boolean,
        val paused: Boolean,
        val interval: Int,
    )
}
