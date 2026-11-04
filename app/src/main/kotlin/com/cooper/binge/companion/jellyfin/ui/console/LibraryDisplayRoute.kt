package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.DisplayList
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.METADATA_DUMMY_CHAPTER
import kotlinx.serialization.json.JsonPrimitive

/** The library display and metadata page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun LibraryDisplayRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    openItem: (String) -> Unit,
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
        displayActions(
            display,
            onEdit = {
                display.itemOpened()
                openItem(it.name)
            },
            onBack = onBack,
        ),
        profile,
        folders,
        FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry),
    )
}

/** One list's editor over the page's view model, which its page shares. */
@Composable
internal fun LibraryDisplayListRoute(
    graph: AppGraph,
    list: String,
    onBack: () -> Unit,
) {
    val display: LibraryDisplayViewModel =
        viewModel(factory = viewModelFactory { initializer { LibraryDisplayViewModel(graph.connection) } })
    val state by display.state.collectAsStateWithLifecycle()
    val displayList = DisplayList.entries.firstOrNull { it.name == list }
    // A list this release doesn't know has nothing to edit: close it.
    LaunchedEffect(displayList) { if (displayList == null) onBack() }
    displayList?.let { LibraryDisplayListScreen(state, it, displayActions(display, onBack = onBack)) }
}

private fun displayActions(
    display: LibraryDisplayViewModel,
    onEdit: (DisplayList) -> Unit = {},
    onBack: () -> Unit,
) = LibraryDisplayActions(
    onFlag = { key, on -> display.set(key, JsonPrimitive(on)) },
    onText = { key, text -> display.set(key, JsonPrimitive(text)) },
    onSeconds = { display.set(METADATA_DUMMY_CHAPTER, JsonPrimitive(it)) },
    onSheet = display::sheet,
    onEdit = onEdit,
    onAdd = display::add,
    onRemove = display::remove,
    onRetry = display::load,
    onRetrySave = display::retrySave,
    onBack = onBack,
)
