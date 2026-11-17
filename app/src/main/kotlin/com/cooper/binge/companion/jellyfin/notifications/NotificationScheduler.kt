package com.cooper.binge.companion.jellyfin.notifications

import android.content.Context
import com.cooper.homelab.core.notifications.PollScheduler
import com.cooper.homelab.core.notifications.WorkManagerPollScheduler

/** The name WorkManager keeps the poll under. It is an on-disk format: the unique work an earlier version scheduled carries on. */
private const val WORK_NAME = "activity_notifications"

/** The poll as unique periodic work, kept across restarts and updated in place when scheduled again. */
fun workManagerPollScheduler(context: Context): PollScheduler = WorkManagerPollScheduler(context, WORK_NAME, NotificationWorker::class.java)
