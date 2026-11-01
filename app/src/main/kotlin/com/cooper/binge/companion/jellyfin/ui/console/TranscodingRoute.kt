package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile

/** The playback and transcoding page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun TranscodingRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onBack: () -> Unit,
) {
    val transcoding: TranscodingViewModel = viewModel(factory = viewModelFactory { initializer { TranscodingViewModel(graph.connection) } })
    val browser: FolderBrowserViewModel = viewModel(factory = viewModelFactory { initializer { FolderBrowserViewModel(graph.connection) } })
    val state by transcoding.state.collectAsStateWithLifecycle()
    val folders by browser.state.collectAsStateWithLifecycle()
    val sheet = (state as? TranscodingUiState.Ready)?.sheet
    // The folder sheet browses from the server's roots each time it opens.
    LaunchedEffect(sheet) { if (sheet == TranscodingSheet.TempFolder) browser.start() }
    TranscodingScreen(
        state,
        TranscodingActions(
            onEncoding = transcoding::setEncoding,
            onServer = transcoding::setServer,
            onCodec = transcoding::setCodec,
            onSheet = transcoding::sheet,
            onNumber = transcoding::saveNumber,
            onRetry = transcoding::load,
            onBack = onBack,
        ),
        profile,
        folders,
        FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry),
    )
}
