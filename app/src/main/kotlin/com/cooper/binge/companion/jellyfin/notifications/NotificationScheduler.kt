package com.cooper.binge.companion.jellyfin.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val WORK_NAME = "activity_notifications"

/** Schedules or cancels the periodic poll; seamed so the planner and the reactor are tested against a fake. */
interface NotificationScheduler {
    /** Runs the poll about every [intervalMinutes], or moves a poll already scheduled to that interval. Android may run it less often. */
    fun schedule(intervalMinutes: Int)

    fun cancel()
}

/** The poll as unique periodic work, kept across restarts and updated in place when re-scheduled. */
class WorkManagerNotificationScheduler(
    private val context: Context,
) : NotificationScheduler {
    override fun schedule(intervalMinutes: Int) {
        val request =
            PeriodicWorkRequestBuilder<NotificationWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
