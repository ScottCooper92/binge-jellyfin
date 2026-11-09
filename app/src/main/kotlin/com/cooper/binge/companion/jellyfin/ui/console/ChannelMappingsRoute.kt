package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The channel mappings of the guide data provider [providerId], opened from the Live TV page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun ChannelMappingsRoute(
    graph: AppGraph,
    providerId: String,
    onBack: () -> Unit,
) {
    val mappings: ChannelMappingsViewModel =
        viewModel(key = providerId, factory = viewModelFactory { initializer { ChannelMappingsViewModel(graph.connection, providerId) } })
    val state by mappings.state.collectAsStateWithLifecycle()
    ChannelMappingsScreen(
        state,
        ChannelMappingsActions(
            onSelect = mappings::select,
            onSearch = mappings::search,
            onPick = mappings::pick,
            onRetry = mappings::load,
            onBack = onBack,
        ),
    )
}
