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
import com.cooper.binge.companion.jellyfin.server.METADATA_DUMMY_CHAPTER
import kotlinx.serialization.json.JsonPrimitive

/** The library display and metadata page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun LibraryDisplayRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onBack: () -> Unit,
) {
    val display: LibraryDisplayViewModel =
        viewModel(factory = viewModelFactory { initializer { LibraryDisplayViewModel(graph.connection) } })
    val browser: FolderBrowserViewModel = viewModel(factory = viewModelFactory { initializer { FolderBrowserViewModel(graph.connection) } })
    val state by display.state.collectAsStateWithLifecycle()
    val folders by browser.state.collectAsStateWithLifecycle()
    val sheet = (state as? LibraryDisplayUiState.Ready)?.sheet
    // The metadata folder sheet browses from the server's roots each time it opens.
    LaunchedEffect(sheet) { if (sheet == DisplaySheet.MetadataPath) browser.start() }
    LibraryDisplayScreen(
        state,
        LibraryDisplayActions(
            onFlag = { key, on -> display.set(key, JsonPrimitive(on)) },
            onText = { key, text -> display.set(key, JsonPrimitive(text)) },
            onSeconds = { display.set(METADATA_DUMMY_CHAPTER, JsonPrimitive(it)) },
            onSheet = display::sheet,
            onEdit = display::edit,
            onAdd = display::add,
            onRemove = display::remove,
            onSave = display::saveList,
            onRetry = display::load,
            onBack = onBack,
        ),
        profile,
        folders,
        FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry),
    )
}
