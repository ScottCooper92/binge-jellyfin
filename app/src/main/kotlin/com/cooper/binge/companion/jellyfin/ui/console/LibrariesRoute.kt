package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LIBRARY_REFRESH_DAYS
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import kotlinx.serialization.json.JsonPrimitive

/**
 * The libraries page, whose event socket closes with the page. A library opened from it, and each of that library's own
 * pages, is an entry of its own on the stack ([LibraryItemRoute]), which reads the libraries again as it changes or closes.
 */
@Composable
internal fun LibrariesRoute(
    graph: AppGraph,
    openItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val libraries: LibrariesViewModel = viewModel(factory = viewModelFactory { initializer { LibrariesViewModel(graph.connection) } })
    val browser: FolderBrowserViewModel = viewModel(factory = viewModelFactory { initializer { FolderBrowserViewModel(graph.connection) } })
    val state by libraries.state.collectAsStateWithLifecycle()
    val folders by browser.state.collectAsStateWithLifecycle()
    val adding = (state as? LibrariesUiState.Ready)?.adding == true
    // Each sheet that browses starts at the server's roots.
    LaunchedEffect(adding) { if (adding) browser.start() }
    LibrariesScreen(
        state,
        LibrariesActions(
            onScan = libraries::scan,
            onScanEverything = libraries::scanEverything,
            onStopEverything = libraries::stopEverything,
            // A library the server hasn't given an id yet has no page: nothing on it could be saved.
            onOpen = { if (it.id.isNotEmpty()) openItem(libraryItem(it.id)) },
            onAskAdd = libraries::ask,
            onAdd = libraries::add,
            onRetry = libraries::load,
            onRefresh = libraries::pull,
            onBack = onBack,
        ),
        folders = folders,
        folderActions = FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry),
    )
}

/** One library's screen, or one of its pages, over the libraries page's view model, which its page shares. */
@Composable
internal fun LibraryItemRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    item: String,
    openSubItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val libraries: LibrariesViewModel = viewModel(factory = viewModelFactory { initializer { LibrariesViewModel(graph.connection) } })
    val browser: FolderBrowserViewModel = viewModel(factory = viewModelFactory { initializer { FolderBrowserViewModel(graph.connection) } })
    val state by libraries.state.collectAsStateWithLifecycle()
    val folders by browser.state.collectAsStateWithLifecycle()
    val id = libraryItemId(item)
    val page = libraryItemPage(item)
    val ready = state as? LibrariesUiState.Ready
    val library = ready?.libraries?.firstOrNull { it.id == id }
    // A library the server no longer has has no page to stay on.
    val gone = ready != null && library == null
    LaunchedEffect(gone) { if (gone) onBack() }
    // The list reads the libraries again when this entry leaves the stack (and on a rotation, one quiet read), where it shows what changed.
    DisposableEffect(Unit) { onDispose { libraries.refresh() } }
    val folderActions = FolderActions(onEnter = browser::enter, onUp = browser::up, onRetry = browser::retry)
    when {
        library != null ->
            LibraryLoadedRoute(
                graph,
                profile,
                library,
                page,
                folders,
                folderActions,
                browser::start,
                libraries::refresh,
                openSubItem,
                onBack,
            )
        state is LibrariesUiState.Failed ->
            LibraryScreen(
                LibraryUiState.Failed((state as LibrariesUiState.Failed).failure),
                LibraryActions(onRetry = libraries::load, onBack = onBack),
                profile,
                page = page,
            )
        else -> LibraryScreen(LibraryUiState.Loading, LibraryActions(onBack = onBack), profile, page = page)
    }
}

/** One library's page, with its own view model, shared by the library's screens. */
@Composable
private fun LibraryLoadedRoute(
    graph: AppGraph,
    profile: JellyfinServerProfile,
    library: ServerLibrary,
    page: LibraryPage,
    folders: FolderBrowseState,
    folderActions: FolderActions,
    browse: () -> Unit,
    refreshList: () -> Unit,
    openSubItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val model: LibraryViewModel =
        viewModel(key = library.id, factory = viewModelFactory { initializer { LibraryViewModel(graph.connection, library) } })
    val state by model.state.collectAsStateWithLifecycle()
    val ready = state as? LibraryUiState.Ready
    // A sheet or confirm, and what the last screen reported, were left on that screen. Once per opening: the flag
    // survives the Activity's recreation, which must keep a sheet that is open.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            opened = true
            if (page == LibraryPage.Main) model.itemOpened()
        }
    }
    // A refusal left on a page isn't replayed on the screen Back returns to, whether Back is the bar's or the system's.
    BackHandler(enabled = page != LibraryPage.Main) {
        model.itemOpened()
        onBack()
    }
    LaunchedEffect(ready?.dialog) { if (ready?.dialog == LibraryDialog.AddFolder) browse() }
    // A removed library has no page to stay on.
    LaunchedEffect(ready?.gone) { if (ready?.gone == true) onBack() }
    // A rename or a folder shows in the list beside it, where there is one, as it is made.
    var listed by remember { mutableStateOf(ready?.library) }
    LaunchedEffect(ready?.library) {
        if (ready != null && ready.library != listed) {
            listed = ready.library
            refreshList()
        }
    }
    LibraryScreen(
        state,
        LibraryActions(
            onFlag = { key, on -> model.set(key, JsonPrimitive(on)) },
            onText = { key, text -> model.set(key, JsonPrimitive(text)) },
            onDays = { model.set(LIBRARY_REFRESH_DAYS, JsonPrimitive(it)) },
            onPage = { openSubItem(libraryPageItem(library.id, it)) },
            onAsk = model::ask,
            onRename = model::rename,
            onAddFolder = model::addFolder,
            onRemoveFolder = model::removeFolder,
            onRemove = model::remove,
            onProvider = model::setProvider,
            onJson = model::set,
            onMove = model::moveProvider,
            onFetcher = model.fetcherEdits::set,
            onMoveFetcher = model.fetcherEdits::move,
            onRetry = model::load,
            onRetrySave = model::retrySave,
            onBack = onBack,
        ),
        profile,
        folders,
        folderActions,
        page,
    )
}

/**
 * The item keys of a library, and of one of its pages, on the stack: `library:<id>`, then `library:<id>/<page>` over it
 * (`languages`, `providers:<group>`, `provider:<group>:<name>`, `fetchers:<kind>`, `fetcher:<kind>:<type>:<name>`).
 */
internal fun libraryItem(id: String) = "library:$id"

internal fun libraryPageItem(
    id: String,
    page: LibraryPage,
): String =
    libraryItem(id) +
        when (page) {
            LibraryPage.Main -> ""
            LibraryPage.Languages -> "/languages"
            is LibraryPage.Providers -> "/providers:${page.group.name}"
            is LibraryPage.One -> "/provider:${page.group.name}:${page.name}"
            is LibraryPage.Fetchers -> "/fetchers:${page.kind.name}"
            is LibraryPage.Fetcher -> "/fetcher:${page.kind.name}:${page.type}:${page.name}"
        }

/** The library [item] is of, a key made by [libraryItem] or [libraryPageItem]. */
internal fun libraryItemId(item: String) = item.removePrefix("library:").substringBefore('/')

/** The page [item] opens: the library itself for a key that names none, or one this release does not know. */
internal fun libraryItemPage(item: String): LibraryPage {
    val rest = item.substringAfter('/', "")
    val parts = rest.split(':', limit = 4)
    return when (parts[0]) {
        "languages" -> LibraryPage.Languages
        "providers" -> providerGroup(parts.getOrNull(1))?.let { LibraryPage.Providers(it) }
        "provider" ->
            providerGroup(parts.getOrNull(1))?.let { group ->
                parts.getOrNull(2)?.let { LibraryPage.One(group, rest.substringAfter(":${group.name}:")) }
            }
        "fetchers" -> fetcherKind(parts.getOrNull(1))?.let { LibraryPage.Fetchers(it) }
        "fetcher" ->
            fetcherKind(parts.getOrNull(1))?.let { kind ->
                val type = parts.getOrNull(2)
                val name = parts.getOrNull(3)
                if (type != null && name != null) LibraryPage.Fetcher(kind, type, name) else null
            }
        else -> null
    } ?: LibraryPage.Main
}

private fun providerGroup(name: String?) = ProviderGroup.entries.firstOrNull { it.name == name }

private fun fetcherKind(name: String?) = FetcherKind.entries.firstOrNull { it.name == name }
