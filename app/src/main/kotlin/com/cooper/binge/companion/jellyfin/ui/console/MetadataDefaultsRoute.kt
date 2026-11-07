package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cooper.binge.companion.jellyfin.AppGraph
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.FetcherKind

/** The metadata providers by item type page. Kept out of `ConsoleRoutes.kt`, which is at its function limit. */
@Composable
internal fun MetadataDefaultsRoute(
    graph: AppGraph,
    openItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val metadata: MetadataDefaultsViewModel =
        viewModel(factory = viewModelFactory { initializer { MetadataDefaultsViewModel(graph.connection) } })
    val state by metadata.state.collectAsStateWithLifecycle()
    val open = LocalOpenItem.current
    LaunchedEffect(open == null) { if (open == null) metadata.itemClosed() }
    MetadataDefaultsScreen(
        state,
        metadataActions(
            metadata,
            onOpen = {
                metadata.itemOpened()
                openItem(metadataTypeItem(it))
            },
            onBack = onBack,
        ),
    )
}

/** One type's screen, or a provider's of that type, over the page's view model, which its page shares. */
@Composable
internal fun MetadataItemRoute(
    connection: Connection,
    item: String,
    openSubItem: (String) -> Unit,
    onBack: () -> Unit,
) {
    val metadata: MetadataDefaultsViewModel =
        viewModel(factory = viewModelFactory { initializer { MetadataDefaultsViewModel(connection) } })
    val state by metadata.state.collectAsStateWithLifecycle()
    // What this screen reported is said here and isn't replayed by the screen beneath it once this one closes. Back clears it
    // before the screen beneath is shown; leaving by any other way clears it on disposal.
    DisposableEffect(Unit) { onDispose { metadata.itemClosed() } }
    val type = metadataItemType(item)
    val actions =
        metadataActions(
            metadata,
            onOpenFetcher = { openType, fetcher ->
                metadata.itemOpened()
                openSubItem(metadataFetcherItem(openType, fetcher))
            },
            onBack = {
                metadata.itemClosed()
                onBack()
            },
        )
    val fetcher = metadataItemFetcher(item)
    if (fetcher == null) MetadataTypeScreen(state, type, actions) else MetadataFetcherScreen(state, type, fetcher, actions)
}

private fun metadataActions(
    metadata: MetadataDefaultsViewModel,
    onOpen: (String) -> Unit = {},
    onOpenFetcher: (String, OpenFetcher) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) = MetadataDefaultsActions(
    onOpen = onOpen,
    onOpenFetcher = onOpenFetcher,
    onSet = metadata::set,
    onMove = metadata::move,
    onRetry = metadata::load,
    onRetrySave = metadata::retrySave,
    onBack = onBack,
)

/** The item keys of an item type, and of one of its providers, on the stack: `type:Movie`, `type:Movie/Metadata:TheMovieDb`. */
internal fun metadataTypeItem(type: String) = "type:$type"

internal fun metadataFetcherItem(
    type: String,
    fetcher: OpenFetcher,
) = "type:$type/${fetcher.kind.name}:${fetcher.name}"

/** The item type of [item], a key made by [metadataTypeItem] or [metadataFetcherItem]. */
internal fun metadataItemType(item: String) = item.removePrefix("type:").substringBefore('/')

/** The provider [item] opens, or null for a key that opens just a type (or one that names a kind this release does not know). */
internal fun metadataItemFetcher(item: String): OpenFetcher? {
    val rest = item.substringAfter('/', "").takeIf { it.isNotEmpty() } ?: return null
    val kind = FetcherKind.entries.firstOrNull { it.name == rest.substringBefore(':') } ?: return null
    return OpenFetcher(kind, rest.substringAfter(':'))
}
