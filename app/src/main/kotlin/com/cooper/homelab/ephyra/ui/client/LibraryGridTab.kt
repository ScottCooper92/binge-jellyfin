package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.libraries.LibraryFilterDraft
import com.cooper.homelab.ephyra.client.libraries.LibraryFilterDraftSaver
import com.cooper.homelab.ephyra.client.libraries.LibraryGridActions
import com.cooper.homelab.ephyra.client.libraries.LibraryGridScreen
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * A library's screen, opened from the Libraries tab: its titles paged in the order and under the filters the user chose. [onOpen] is a
 * title's tap and gets its kind and id; the shell opens its page. The order and filters are kept across a process death, as the route
 * is, so a library comes back as it was left.
 */
@Composable
fun LibraryGridTab(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    libraryId: String,
    onBack: () -> Unit,
    onOpen: (kind: ItemKind, id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var saved by rememberSaveable(libraryId, stateSaver = LibraryFilterDraftSaver) { mutableStateOf(LibraryFilterDraft()) }
    val model: LibraryGridTabViewModel =
        viewModel(
            key = "library-grid-$libraryId",
            factory =
                viewModelFactory {
                    initializer { LibraryGridTabViewModel(graph.connection, graph.ioDispatcher, profile, libraryId, saved) }
                },
        )
    LaunchedEffect(model) { model.start() }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.controls.applied) { saved = state.controls.applied }
    LibraryGridScreen(
        state = state,
        imageUrl = model::imageUrl,
        actions =
            LibraryGridActions(
                onOpenItem = onOpen,
                onLoadMore = model::loadMore,
                onRetry = model::retry,
                onBack = onBack,
                onFiltersOpened = model::filtersOpened,
                onApply = model::apply,
                onClearChip = model::clearChip,
                onClearFilters = model::clearFilters,
            ),
        modifier = modifier,
    )
}
