package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The API keys page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun ApiKeysRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val keys: ApiKeysViewModel = viewModel(factory = viewModelFactory { initializer { ApiKeysViewModel(graph.connection) } })
    val state by keys.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ApiKeysScreen(
        state,
        ApiKeysActions(
            onAskCreate = keys::askCreate,
            onCreate = keys::create,
            onCopy = { copyApiKey(context, it) },
            onDismissCreated = keys::dismissCreated,
            onAskRemove = keys::askRemove,
            onRemove = keys::remove,
            onRetry = keys::load,
            onBack = onBack,
        ),
    )
}
