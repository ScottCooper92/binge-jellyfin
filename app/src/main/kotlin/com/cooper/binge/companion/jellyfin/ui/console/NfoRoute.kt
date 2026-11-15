package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The NFO page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun NfoRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val nfo: NfoViewModel = viewModel(factory = viewModelFactory { initializer { NfoViewModel(graph.connection) } })
    val state by nfo.state.collectAsStateWithLifecycle()
    NfoScreen(
        state,
        NfoActions(
            onSet = nfo::set,
            onUser = nfo::setUser,
            onDateFormat = nfo::setDateFormat,
            onSheet = nfo::sheet,
            onRetry = nfo::load,
            onRetrySave = nfo::retrySave,
            onBack = onBack,
        ),
    )
}
