package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The metadata providers by item type page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun MetadataDefaultsRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val metadata: MetadataDefaultsViewModel =
        viewModel(factory = viewModelFactory { initializer { MetadataDefaultsViewModel(graph.connection) } })
    val state by metadata.state.collectAsStateWithLifecycle()
    PageItemDepth(open = (state as? MetadataDefaultsUiState.Ready)?.type != null) {
        MetadataDefaultsScreen(
            state,
            MetadataDefaultsActions(
                onOpen = metadata::open,
                onOpenFetcher = metadata::openFetcher,
                onSet = metadata::set,
                onMove = metadata::move,
                onRetry = metadata::load,
                onRetrySave = metadata::retrySave,
                onBack = onBack,
            ),
        )
    }
}
