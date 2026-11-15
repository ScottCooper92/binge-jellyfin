package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.tip.TipEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * How a tip ended: a short thanks or "waiting" as a snackbar, and a failure as a dialog, because it
 * names a cause and a next step.
 */
@Composable
internal fun TipResults(
    events: Flow<TipEvent>,
    snackbarHostState: SnackbarHostState,
    onFailed: () -> Unit,
) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            when (event) {
                TipEvent.Thanks ->
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.tip_thanks),
                        SnackbarMessageKind.Confirmation,
                    )
                TipEvent.Pending ->
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.tip_pending),
                        SnackbarMessageKind.Confirmation,
                    )
                TipEvent.Failed ->
                    onFailed()
            }
        }
    }
}
