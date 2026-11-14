package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.FormScreen
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.formSaveStatus
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.fetcherPlace
import com.cooper.binge.companion.jellyfin.server.fetchers
import com.binge.designsystem.R as DesR

/** What the metadata defaults page can ask of its view model, and of the console. */
data class MetadataDefaultsActions(
    val onOpen: (String) -> Unit = {},
    val onSet: (type: String, kind: FetcherKind, name: String, enabled: Boolean) -> Unit = { _, _, _, _ -> },
    val onOpenFetcher: (type: String, OpenFetcher) -> Unit = { _, _ -> },
    val onMove: (type: String, kind: FetcherKind, name: String, by: Int) -> Unit = { _, _, _, _ -> },
    val onRetry: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onBack: () -> Unit = {},
)

private const val LOADING_ROWS = 8

/** The words for an item type as the server names it, or the name itself for one this app has no word for. */
@Composable
internal fun itemTypeName(type: String): String =
    when (type) {
        "Movie" -> stringResource(R.string.metadata_type_movie)
        "Series" -> stringResource(R.string.metadata_type_series)
        "Season" -> stringResource(R.string.metadata_type_season)
        "Episode" -> stringResource(R.string.metadata_type_episode)
        "Book" -> stringResource(R.string.metadata_type_book)
        "MusicVideo" -> stringResource(R.string.metadata_type_music_video)
        "MusicArtist" -> stringResource(R.string.metadata_type_artist)
        "MusicAlbum" -> stringResource(R.string.metadata_type_album)
        "Audio" -> stringResource(R.string.metadata_type_song)
        "Video" -> stringResource(R.string.metadata_type_video)
        "Photo" -> stringResource(R.string.metadata_type_photo)
        "BoxSet" -> stringResource(R.string.metadata_type_collection)
        "Playlist" -> stringResource(R.string.metadata_type_playlist)
        else -> type
    }

/**
 * The server's metadata and image provider defaults for each kind of title: a list of the kinds, each opening its own
 * screen ([MetadataTypeScreen], then [MetadataFetcherScreen]) with a switch for each provider. Each switch saves as it's
 * made. [open] is the key of the item open over the page, whose type is marked in the list.
 */
@Composable
fun MetadataDefaultsScreen(
    state: MetadataDefaultsUiState,
    actions: MetadataDefaultsActions,
    open: String? = LocalOpenItem.current,
) {
    val ready = state as? MetadataDefaultsUiState.Ready
    val host = remember { SnackbarHostState() }
    FormScreen(
        title = stringResource(R.string.metadata_title),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady = metadataNotReady(state, actions),
    ) { _ ->
        if (ready != null) {
            TypeList(ready, actions, open?.let(::metadataItemType))
            // The screen of an item says what the server refused while it is open.
            RefusedMessage(ready.refused.takeIf { open == null }, host)
        }
    }
}

/** One item type's screen: a row for each provider it can use, opening that provider's own. */
@Composable
fun MetadataTypeScreen(
    state: MetadataDefaultsUiState,
    type: String,
    actions: MetadataDefaultsActions,
) {
    val ready = state as? MetadataDefaultsUiState.Ready
    val host = remember { SnackbarHostState() }
    // A type the server no longer lists has nothing to show: close it.
    LaunchedEffect(ready != null && type !in ready.types()) { if (ready != null && type !in ready.types()) actions.onBack() }
    FormScreen(
        title = itemTypeName(type),
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady = metadataNotReady(state, actions),
    ) { _ ->
        if (ready != null) {
            TypePage(type, ready, actions)
            RefusedMessage(ready.refused, host)
        }
    }
}

/** One provider of a type: whether the defaults use it, and its place in the order. */
@Composable
fun MetadataFetcherScreen(
    state: MetadataDefaultsUiState,
    type: String,
    fetcher: OpenFetcher,
    actions: MetadataDefaultsActions,
) {
    val ready = state as? MetadataDefaultsUiState.Ready
    val host = remember { SnackbarHostState() }
    val gone = ready != null && ready.fetchers(type, fetcher.kind).none { it.name.equals(fetcher.name, ignoreCase = true) }
    LaunchedEffect(gone) { if (gone) actions.onBack() }
    FormScreen(
        title = fetcher.name,
        bar = ScreenBar.Small,
        onBack = actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady = metadataNotReady(state, actions),
    ) { _ ->
        if (ready != null) {
            FetcherPage(type, fetcher, ready, actions)
            RefusedMessage(ready.refused, host)
        }
    }
}

/** What a screen of this page shows until the defaults are there. */
private fun metadataNotReady(
    state: MetadataDefaultsUiState,
    actions: MetadataDefaultsActions,
): (@Composable (PaddingValues) -> Unit)? =
    when (state) {
        MetadataDefaultsUiState.Loading -> { padding ->
            ConsoleLoading(padding, LOADING_ROWS, intro = stringResource(R.string.metadata_body))
        }
        is MetadataDefaultsUiState.Failed -> { padding -> ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding)) }
        is MetadataDefaultsUiState.Ready -> null
    }

/** The empty right-hand pane when no type is open. */
@Composable
internal fun MetadataDefaultsPlaceholder() {
    EmptyScreen(
        message = stringResource(R.string.metadata_select),
        title = stringResource(R.string.metadata_select_title),
        icon = Icons.Outlined.Tune,
    )
}

@Composable
private fun TypeList(
    state: MetadataDefaultsUiState.Ready,
    actions: MetadataDefaultsActions,
    openType: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.metadata_body))
        ItemGroup(
            title = null,
            modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            rows =
                state.types().map { type ->
                    val off = FetcherKind.entries.sumOf { kind -> state.fetchers(type, kind).count { !it.enabled } }
                    ListItem(
                        icon = Icons.Outlined.Tune,
                        label = itemTypeName(type),
                        detail =
                            if (off == 0) {
                                stringResource(R.string.metadata_all_on)
                            } else {
                                pluralStringResource(R.plurals.metadata_turned_off, off, off)
                            },
                        selected = type == openType,
                        onClick = { actions.onOpen(type) },
                    )
                },
        )
    }
}

@Composable
private fun TypePage(
    type: String,
    state: MetadataDefaultsUiState.Ready,
    actions: MetadataDefaultsActions,
) {
    val groups = FetcherKind.entries.map { kind -> kind to state.fetchers(type, kind) }
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.metadata_type_body))
        if (groups.all { (_, providers) -> providers.isEmpty() }) {
            PageIntro(stringResource(R.string.metadata_type_empty))
        }
        groups.forEach { (kind, providers) ->
            if (providers.isNotEmpty()) {
                ItemGroup(
                    title = stringResource(if (kind == FetcherKind.Metadata) R.string.metadata_fetchers else R.string.metadata_images),
                    rows =
                        providers.map { provider ->
                            ListItem(
                                icon = if (kind == FetcherKind.Metadata) Icons.Outlined.Extension else Icons.Outlined.Image,
                                label = provider.name,
                                detail =
                                    stringResource(
                                        if (provider.enabled) R.string.library_provider_on else R.string.library_provider_off,
                                    ),
                                onClick = { actions.onOpenFetcher(type, OpenFetcher(kind, provider.name)) },
                            )
                        },
                )
            }
        }
    }
}

/** One fetcher of a type: whether the defaults use it, and its place in the order. */
@Composable
private fun FetcherPage(
    type: String,
    open: OpenFetcher,
    state: MetadataDefaultsUiState.Ready,
    actions: MetadataDefaultsActions,
) {
    val all = state.fetchers(type, open.kind)
    val fetcher = all.firstOrNull { it.name.equals(open.name, ignoreCase = true) } ?: return
    val place = state.configuration.fetcherPlace(type, open.kind, state.offered(type, open.kind), fetcher.name)
    OrderedSettingsPage(
        title = null,
        switches =
            listOf(
                ListItem(
                    icon = Icons.Outlined.PowerSettingsNew,
                    label = stringResource(R.string.library_provider_use),
                    toggled = fetcher.enabled,
                    onClick = { actions.onSet(type, open.kind, fetcher.name, !fetcher.enabled) },
                ),
            ),
        place = place?.first,
        size = place?.second ?: 0,
        onMove = { actions.onMove(type, open.kind, fetcher.name, it) },
    )
}

private fun MetadataDefaultsUiState.Ready.fetchers(
    type: String,
    kind: FetcherKind,
) = configuration.fetchers(type, kind, offered(type, kind))
