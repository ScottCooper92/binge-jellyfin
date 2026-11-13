package com.cooper.binge.companion.jellyfin.telemetry

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.cooper.binge.companion.jellyfin.ui.UiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage

/** Reports the screen [state] is showing to [analytics] once each time it changes, by [ScreenNames]' fixed name. */
@Composable
fun ReportScreen(
    state: UiState,
    openPage: ConsolePage?,
    analytics: Analytics,
) {
    val name = ScreenNames.of(state, openPage)
    LaunchedEffect(name) { name?.let(analytics::screen) }
}
