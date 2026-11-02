package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The Advanced page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun AdvancedRoute(
    graph: AppGraph,
    gates: AdvancedGates,
    onOpenServer: () -> Unit,
    onBack: () -> Unit,
) {
    val advanced: AdvancedViewModel = viewModel(factory = viewModelFactory { initializer { AdvancedViewModel(graph.connection) } })
    val state by advanced.state.collectAsStateWithLifecycle()
    AdvancedScreen(
        state,
        AdvancedActions(
            onEdit = advanced::edit,
            onSave = advanced::save,
            onEditList = advanced::editList,
            onSaveList = advanced::saveList,
            onSlowResponseWarning = advanced::setSlowResponseWarning,
            onMetrics = advanced::setMetrics,
            onAllowClientLogUpload = advanced::setAllowClientLogUpload,
            onLegacyAuthorization = advanced::setLegacyAuthorization,
            onOpenServer = onOpenServer,
            onRetry = advanced::load,
            onRetrySave = advanced::retrySave,
            onBack = onBack,
        ),
        gates,
    )
}
