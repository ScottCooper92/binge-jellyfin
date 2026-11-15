package com.cooper.binge.companion.jellyfin.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cooper.binge.companion.jellyfin.R

private const val ACTIVITY_NOTIFICATION_ID = 4301
private const val CONNECTION_NOTIFICATION_ID = 4302
private const val ACTIVITY_CHANNEL = "server_activity"
private const val CONNECTION_CHANNEL = "connection"

/**
 * Posts what the poll found: one notification per check, so a later one replaces an earlier one that was never opened. The
 * text is counts and fixed words. It never names the server, an address or a user, and never carries the access token.
 */
class AndroidNotifier(
    private val context: Context,
) : ActivityNotifier {
    /** App-level notifications on, plus the runtime permission on 13+. A muted channel is not checked: the system drops its posts itself. */
    override fun canPost(): Boolean = mayPost()

    private fun mayPost(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override suspend fun notifyActivity(summary: ActivitySummary) {
        val lines = activityLines(context, summary)
        if (lines.isEmpty()) return
        post(
            id = ACTIVITY_NOTIFICATION_ID,
            channel = ACTIVITY_CHANNEL,
            channelName = R.string.notif_channel_activity_name,
            channelDescription = R.string.notif_channel_activity_description,
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            title = context.getString(R.string.notif_activity_title),
            body = lines.joinToString("\n"),
        )
    }

    override suspend fun notifyConnectionProblem() =
        post(
            id = CONNECTION_NOTIFICATION_ID,
            channel = CONNECTION_CHANNEL,
            channelName = R.string.notif_channel_connection_name,
            channelDescription = R.string.notif_channel_connection_description,
            importance = NotificationManager.IMPORTANCE_HIGH,
            title = context.getString(R.string.notif_connection_title),
            body = context.getString(R.string.notif_connection_body),
        )

    override suspend fun cancelConnectionProblem() = NotificationManagerCompat.from(context).cancel(CONNECTION_NOTIFICATION_ID)

    override fun cancelActivity() = NotificationManagerCompat.from(context).cancel(ACTIVITY_NOTIFICATION_ID)

    /** [mayPost] guards the permission, but lint follows only a check inlined in the same method. */
    @SuppressLint("MissingPermission")
    private fun post(
        id: Int,
        channel: String,
        channelName: Int,
        channelDescription: Int,
        importance: Int,
        title: String,
        body: String,
    ) {
        if (!mayPost()) return
        ensureChannel(channel, channelName, channelDescription, importance)
        val notification =
            NotificationCompat
                .Builder(context, channel)
                .setSmallIcon(R.drawable.ic_companion)
                .setContentTitle(title)
                .setContentText(body.lineSequence().first())
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(openApp(id))
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun openApp(id: Int): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun ensureChannel(
        id: String,
        name: Int,
        description: Int,
        importance: Int,
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(id, context.getString(name), importance).apply { this.description = context.getString(description) },
        )
    }
}

/** One line per kind of finding, in the order of how much each matters. Empty when there is nothing to say. */
internal fun activityLines(
    context: Context,
    summary: ActivitySummary,
): List<String> =
    listOfNotNull(
        summary.lockouts.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.notif_lockouts, it, it) },
        summary.failedSignIns.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.notif_failed_sign_ins, it, it) },
        summary.problems.takeIf { it > 0 }?.let { context.resources.getQuantityString(R.plurals.notif_problems, it, it) },
    )
