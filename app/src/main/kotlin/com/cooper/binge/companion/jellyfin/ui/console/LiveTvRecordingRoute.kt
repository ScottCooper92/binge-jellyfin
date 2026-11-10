package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph

/** The Live TV recording page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun LiveTvRecordingRoute(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    val recording: LiveTvRecordingViewModel =
        viewModel(factory = viewModelFactory { initializer { LiveTvRecordingViewModel(graph.connection) } })
    val browser: FolderBrowserViewModel = viewModel(factory = viewModelFactory { initializer { FolderBrowserViewModel(graph.connection) } })
    val state by recording.state.collectAsStateWithLifecycle()
    val folders by browser.state.collectAsStateWithLifecycle()
    val sheet = (state as? LiveTvRecordingUiState.Ready)?.sheet
    LaunchedEffect(sheet) { if (sheet is RecordingSheet.Folder) browser.start() }
    LiveTvRecordingScreen(
        state,
        LiveTvRecordingActions(
            onSet = recording::set,
            onSheet = recording::sheet,
            onFolder = recording::saveFolder,
            onRetry = recording::load,
            onRetrySave = recording::retrySave,
            onBack = onBack,
        ),
        folders,
        FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry),
    )
}
