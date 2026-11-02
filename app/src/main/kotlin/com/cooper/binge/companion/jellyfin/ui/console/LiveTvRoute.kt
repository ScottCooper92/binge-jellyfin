package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount

/** The Live TV page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun LiveTvRoute(
    graph: AppGraph,
    account: JellyfinAccount,
    onOpenTasks: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val liveTv: LiveTvViewModel =
        viewModel(factory = viewModelFactory { initializer { LiveTvViewModel(graph.connection, withGuide = account.canAccessLiveTv) } })
    val guide: LiveTvGuideViewModel =
        viewModel(factory = viewModelFactory { initializer { LiveTvGuideViewModel(graph.connection, liveTv::refresh) } })
    val state by liveTv.state.collectAsStateWithLifecycle()
    val guideState by guide.state.collectAsStateWithLifecycle()
    LiveTvScreen(
        state,
        LiveTvActions(onOpenTasks = onOpenTasks, onRetry = liveTv::load, onBack = onBack),
        guide = guideState,
        guideActions =
            LiveTvGuideActions(
                onAdd = guide::add,
                onEdit = guide::edit,
                onSave = guide::save,
                onDismissForm = guide::dismissForm,
                onAskRemove = guide::askRemove,
                onCancelRemove = guide::cancelRemove,
                onRemove = guide::remove,
                onDismissProblem = guide::dismissProblem,
            ),
    )
}
