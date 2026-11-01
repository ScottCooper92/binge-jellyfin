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
import com.cooper.binge.companion.jellyfin.server.LIBRARY_REFRESH_DAYS
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import kotlinx.serialization.json.JsonPrimitive

/**
 * The libraries page, whose event socket closes with the page, and the page of the library opened from it.
 * Back from a library comes back to the list, which reads the libraries again.
 */
@Composable
internal fun LibrariesRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    onBack: () -> Unit,
) {
    val libraries: LibrariesViewModel = viewModel(factory = viewModelFactory { initializer { LibrariesViewModel(graph.connection) } })
    val browser: FolderBrowserViewModel = viewModel(factory = viewModelFactory { initializer { FolderBrowserViewModel(graph.connection) } })
    val state by libraries.state.collectAsStateWithLifecycle()
    val folders by browser.state.collectAsStateWithLifecycle()
    val folderActions = FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry)
    val opened = (state as? LibrariesUiState.Ready)?.let { ready -> ready.libraries.firstOrNull { it.id == ready.opened } }
    val adding = (state as? LibrariesUiState.Ready)?.adding == true
    // Each sheet that browses starts at the server's roots.
    LaunchedEffect(adding) { if (adding) browser.start() }
    if (opened != null) {
        LibraryRoute(graph, profile, opened, folders, folderActions, browser::start) {
            libraries.open(null)
            libraries.refresh()
        }
    } else {
        LibrariesScreen(
            state,
            LibrariesActions(
                onScan = libraries::scan,
                onScanEverything = libraries::scanEverything,
                onStopEverything = libraries::stopEverything,
                onOpen = { libraries.open(it.id) },
                onAskAdd = libraries::ask,
                onAdd = libraries::add,
                onRetry = libraries::load,
                onBack = onBack,
            ),
            folders = folders,
            folderActions = folderActions,
        )
    }
}

/** One library's page, with its own view model, kept while the page is open. */
@Composable
private fun LibraryRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    library: ServerLibrary,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    browse: () -> Unit,
    onBack: () -> Unit,
) {
    val model: LibraryViewModel =
        viewModel(key = library.id, factory = viewModelFactory { initializer { LibraryViewModel(graph.connection, library) } })
    val state by model.state.collectAsStateWithLifecycle()
    val ready = state as? LibraryUiState.Ready
    LaunchedEffect(ready?.dialog) { if (ready?.dialog == LibraryDialog.AddFolder) browse() }
    // A removed library has no page to stay on.
    LaunchedEffect(ready?.gone) { if (ready?.gone == true) onBack() }
    LibraryScreen(
        state,
        LibraryActions(
            onFlag = { key, on -> model.set(key, JsonPrimitive(on)) },
            onText = { key, text -> model.set(key, JsonPrimitive(text)) },
            onDays = { model.set(LIBRARY_REFRESH_DAYS, JsonPrimitive(it)) },
            onPage = model::show,
            onAsk = model::ask,
            onRename = model::rename,
            onAddFolder = model::addFolder,
            onRemoveFolder = model::removeFolder,
            onRemove = model::remove,
            onProvider = model::setProvider,
            onMove = model::moveProvider,
            onRetry = model::load,
            onBack = onBack,
        ),
        profile,
        folders,
        folderActions,
    )
}
