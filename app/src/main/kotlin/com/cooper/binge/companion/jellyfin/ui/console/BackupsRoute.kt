package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion

/** The backups page with its own view model, kept while the page is open and new for each opening. */
@Composable
internal fun BackupsRoute(
    graph: AppGraph,
    serverVersion: JellyfinVersion?,
    onBack: () -> Unit,
) {
    val backups: BackupsViewModel = viewModel(factory = viewModelFactory { initializer { BackupsViewModel(graph.connection) } })
    val state by backups.state.collectAsStateWithLifecycle()
    BackupsScreen(
        state,
        BackupsActions(
            onAskCreate = backups::askCreate,
            onChoose = backups::choose,
            onCreate = backups::create,
            onDismissCreation = backups::dismissCreation,
            onOpen = backups::open,
            onAskRestore = backups::askRestore,
            onRestore = backups::restore,
            onDismissRestore = backups::dismissRestore,
            onRetry = backups::load,
            onRefresh = backups::refresh,
            onBack = onBack,
        ),
        serverVersion = serverVersion,
    )
}
