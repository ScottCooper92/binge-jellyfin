package com.cooper.binge.companion.jellyfin.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeChoice
import com.binge.designsystem.component.BingeChoiceList
import com.binge.designsystem.component.BingeChoiceSheetContent
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.notifications.DEFAULT_POLL_INTERVAL_MINUTES
import com.cooper.binge.companion.jellyfin.notifications.POLL_INTERVAL_CHOICES

/**
 * The notification group as the This app page shows it, for an administrator: whether the poll is on, how often it runs, whether
 * Android would show what it finds and whether Android holds its checks back. Everything is on the device.
 */
data class NotificationSettings(
    val enabled: Boolean = false,
    val intervalMinutes: Int = DEFAULT_POLL_INTERVAL_MINUTES,
    /** The system would show nothing: notifications are off for the app, or the permission is refused. */
    val blocked: Boolean = false,
    /** The user has set the app's battery use to Unrestricted. */
    val unrestricted: Boolean = true,
)

/**
 * The Notifications group: a switch, then, while it is on, a warning if the system would show nothing, how often to check, and a
 * way to the app's settings while Android holds checks back. The interval choices are a sheet; [IntervalChoices] is its content.
 */
@Composable
internal fun NotificationsGroup(
    settings: NotificationSettings,
    actions: Actions,
) {
    var picking by remember { mutableStateOf(false) }
    val context = LocalContext.current
    ItemGroup(
        title = stringResource(R.string.settings_group_notifications),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Outlined.Notifications,
                    label = stringResource(R.string.notifications_switch),
                    detail = stringResource(R.string.notifications_switch_caption),
                    supportingMaxLines = 3,
                    toggled = settings.enabled,
                    onClick = { actions.onNotificationsEnabled(!settings.enabled) },
                ),
                ListItem(
                    icon = Icons.Outlined.NotificationsOff,
                    iconTint = BingeSentiment.Negative.fill(),
                    label = stringResource(R.string.notifications_blocked),
                    detail = stringResource(R.string.notifications_blocked_caption),
                    detailColor = BingeSentiment.Negative.fill(),
                    supportingMaxLines = 3,
                    destination = ListItemDestination.External,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ).takeIf { settings.enabled && settings.blocked },
                ListItem(
                    icon = Icons.Outlined.Timer,
                    label = stringResource(R.string.notifications_interval),
                    detail = stringResource(settings.intervalMinutes.intervalLabelRes()),
                    onClick = { picking = true },
                ).takeIf { settings.enabled },
                ListItem(
                    icon = Icons.Outlined.BatteryAlert,
                    iconTint = BingeSentiment.Caution.fill(),
                    label = stringResource(R.string.notifications_battery),
                    detail = stringResource(R.string.notifications_battery_caption),
                    supportingMaxLines = 3,
                    destination = ListItemDestination.External,
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                ).takeIf { settings.enabled && !settings.unrestricted },
            ),
    )
    if (picking) {
        val close = { picking = false }
        BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = close) { IntervalChoices(settings, actions, close) }
    }
}

@Composable
internal fun IntervalChoices(
    settings: NotificationSettings,
    actions: Actions,
    close: () -> Unit,
) {
    val choices =
        POLL_INTERVAL_CHOICES.map { minutes ->
            BingeChoice(minutes, stringResource(minutes.intervalLabelRes()), icon = Icons.Outlined.Timer)
        }
    BingeChoiceSheetContent(
        title = stringResource(R.string.notifications_interval),
        choices = BingeChoiceList.Ready(choices),
        selected = settings.intervalMinutes,
        onSelect = { minutes ->
            actions.onNotificationInterval(minutes)
            close()
        },
    )
}

private val INTERVAL_LABELS: Map<Int, Int> =
    mapOf(
        15 to R.string.notifications_interval_15,
        30 to R.string.notifications_interval_30,
        60 to R.string.notifications_interval_60,
        120 to R.string.notifications_interval_120,
        360 to R.string.notifications_interval_360,
    )

/** The words for one of the poll's intervals; a value the settings do not offer reads as the default. */
@StringRes
internal fun Int.intervalLabelRes(): Int = INTERVAL_LABELS[this] ?: R.string.notifications_interval_60
