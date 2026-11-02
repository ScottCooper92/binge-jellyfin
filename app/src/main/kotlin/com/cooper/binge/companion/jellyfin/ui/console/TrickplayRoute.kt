package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The trickplay page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun TrickplayRoute(
    graph: AppGraph,
    keyFrameOnlyAvailable: Boolean,
    onBack: () -> Unit,
) {
    val trickplay: TrickplayViewModel = viewModel(factory = viewModelFactory { initializer { TrickplayViewModel(graph.connection) } })
    val state by trickplay.state.collectAsStateWithLifecycle()
    TrickplayScreen(
        state,
        TrickplayActions(
            onEdit = trickplay::edit,
            onSave = trickplay::save,
            onHwAcceleration = trickplay::setHwAcceleration,
            onHwEncoding = trickplay::setHwEncoding,
            onKeyFrameOnly = trickplay::setKeyFrameOnly,
            onScanBehavior = trickplay::setScanBehavior,
            onProcessPriority = trickplay::setProcessPriority,
            onInterval = trickplay::setInterval,
            onRetry = trickplay::load,
            onRetrySave = trickplay::retrySave,
            onBack = onBack,
        ),
        keyFrameOnlyAvailable = keyFrameOnlyAvailable,
    )
}
