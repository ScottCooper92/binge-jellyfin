package com.cooper.binge.companion.jellyfin.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.jellyfin.AppGraph
import kotlinx.coroutines.launch

/**
 * Reads the poll's settings for the life of the composition and returns how to put them in [Actions]. An administrator gets the
 * group; anyone else gets none, so a non-administrator never sees its rows. What the system allows is read, not observed, so it
 * is read again on resume and after the permission prompt.
 */
@Composable
fun rememberNotificationBinding(
    graph: AppGraph,
    administrator: Boolean,
    /** What the caller has already put in [Actions]: the telemetry choices. */
    base: (Actions) -> Actions,
): (Actions) -> Actions {
    val prefs = graph.notificationPrefs
    val enabled by prefs.enabled.collectAsStateWithLifecycle(initialValue = false)
    val interval by prefs.intervalMinutes.collectAsStateWithLifecycle(initialValue = 60)
    var rechecks by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { rechecks++ }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { rechecks++ }
    val scope = rememberCoroutineScope()
    // Read on each composition that follows a recheck: the system's answers are not observable.
    val blocked = rechecks >= 0 && !graph.notifier.mayPost()
    val unrestricted = rechecks >= 0 && graph.backgroundAccess.isUnrestricted()
    return { given ->
        val actions = base(given)
        if (!administrator) {
            actions
        } else {
            actions.copy(
                notifications = NotificationSettings(enabled, interval, blocked, unrestricted),
                onNotificationsEnabled = { on ->
                    if (on &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    ) {
                        askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    scope.launch { graph.activitySwitch.set(on) }
                },
                onNotificationInterval = { minutes -> scope.launch { prefs.setIntervalMinutes(minutes) } },
            )
        }
    }
}
