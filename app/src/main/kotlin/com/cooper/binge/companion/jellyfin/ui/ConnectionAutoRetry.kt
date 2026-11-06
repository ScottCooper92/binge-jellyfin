package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** The waits between the tries: bounded, so a server that is really down is not asked about for ever. */
internal val AUTO_RETRY_DELAYS_MILLIS = listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

/**
 * Heals a transient failure in place, for as long as the connection problem is on screen and the app is in front. A server
 * that did not answer, or answered something unusable, is asked again after each of [delaysMillis]; leaving the screen, the
 * app, or the problem stops it, and a later relapse starts with a fresh budget. A try the user started is not doubled.
 *
 * A missing local-network permission is not retried on a timer: asking again cannot change it. Coming back to the app, from
 * the system's prompt or its settings, reads it again instead.
 */
@Composable
internal fun ConnectionAutoRetry(
    health: ConnectionHealth,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    delaysMillis: List<Long> = AUTO_RETRY_DELAYS_MILLIS,
) {
    val refreshingNow by rememberUpdatedState(refreshing)
    val refresh by rememberUpdatedState(onRefresh)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(health) {
        if (health != ConnectionHealth.Unreachable && health != ConnectionHealth.CouldNotLoad) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            for (wait in delaysMillis) {
                delay(wait)
                if (!refreshingNow) refresh()
            }
        }
    }
    // Back from the system's prompt or the app's settings, not the first time the screen is resumed.
    var wasPaused by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { wasPaused = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (wasPaused && health == ConnectionHealth.LocalNetworkDenied) refresh()
        wasPaused = false
    }
}
