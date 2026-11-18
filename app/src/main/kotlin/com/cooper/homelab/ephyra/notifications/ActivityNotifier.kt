package com.cooper.homelab.ephyra.notifications

import com.cooper.homelab.core.notifications.PollNotifier

/** What the poll tells the user, seamed off Android so the checker is tested against a fake. The poll's own notices are the shared [PollNotifier]'s. */
interface ActivityNotifier : PollNotifier {
    /** One notification for the whole check, grouping what it found. */
    suspend fun notifyActivity(summary: ActivitySummary)
}
