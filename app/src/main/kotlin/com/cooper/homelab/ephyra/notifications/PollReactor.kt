package com.cooper.homelab.ephyra.notifications

import com.cooper.homelab.core.notifications.PollPauseStore
import com.cooper.homelab.core.notifications.PollReactor
import com.cooper.homelab.core.notifications.PollScheduler

/**
 * What the shared [PollReactor] does for this app's one server: a refused sign-in is told to the user and pauses the poll, which
 * a new sign-in lifts; a user who is no longer an administrator has the switch turned off for them.
 */
fun pollReactor(
    notifier: ActivityNotifier,
    scheduler: PollScheduler,
    prefs: NotificationPrefs,
    anyPollable: suspend () -> Boolean,
): PollReactor =
    PollReactor(
        notifier = notifier,
        scheduler = scheduler,
        pause = PollPauseStore { _, paused -> prefs.setPausedForAuthFailure(paused) },
        onStopPolling = { prefs.setEnabled(false) },
        anyPollable = anyPollable,
    )
