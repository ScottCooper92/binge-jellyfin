package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.component.ListRowSkeletonColumn
import com.binge.designsystem.component.PageIntro
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
    val onOpen: (String?) -> Unit = {},
    val onSet: (type: String, kind: FetcherKind, name: String, enabled: Boolean) -> Unit = { _, _, _, _ -> },
    val onOpenFetcher: (OpenFetcher?) -> Unit = {},
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
 * The server's metadata and image provider defaults for each kind of title: a list of the kinds, and a page for one with a
 * switch for each provider. Each switch saves as it's made.
 */
@Composable
fun MetadataDefaultsScreen(
    state: MetadataDefaultsUiState,
    actions: MetadataDefaultsActions,
) {
    val ready = state as? MetadataDefaultsUiState.Ready
    val type = ready?.type
    val provider = ready?.provider
    val back: () -> Unit = if (provider != null) ({ actions.onOpenFetcher(null) }) else ({ actions.onOpen(null) })
    BackHandler(enabled = type != null, onBack = back)
    val host = remember { SnackbarHostState() }
    FormScreen(
        title = provider?.name ?: type?.let { itemTypeName(it) } ?: stringResource(R.string.metadata_title),
        bar = ScreenBar.Small,
        onBack = if (type != null) back else actions.onBack,
        snackbarHostState = host,
        saveStatus = ready?.save?.let { formSaveStatus(it.status, it.failed, actions.onRetrySave) },
        notReady =
            when (state) {
                MetadataDefaultsUiState.Loading -> { padding -> ListRowSkeletonColumn(contentPadding = padding, count = LOADING_ROWS) }
                is MetadataDefaultsUiState.Failed -> { padding ->
                    ConsoleFailureScreen(state.failure, actions.onRetry, Modifier.padding(padding))
                }
                is MetadataDefaultsUiState.Ready -> null
            },
    ) { _ ->
        if (ready != null) {
            when {
                type == null -> TypeList(ready, actions)
                provider == null -> TypePage(type, ready, actions)
                else -> FetcherPage(type, provider, ready, actions)
            }
            RefusedMessage(ready.refused, host)
        }
    }
}

@Composable
private fun TypeList(
    state: MetadataDefaultsUiState.Ready,
    actions: MetadataDefaultsActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.metadata_body))
        ItemGroup(
            title = null,
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
                                onClick = { actions.onOpenFetcher(OpenFetcher(kind, provider.name)) },
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
    val index = place?.first ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.PowerSettingsNew,
                        label = stringResource(R.string.library_provider_use),
                        toggled = fetcher.enabled,
                        onClick = { actions.onSet(type, open.kind, fetcher.name, !fetcher.enabled) },
                    ),
                ),
        )
        ItemGroup(
            title = stringResource(R.string.home_layout_order),
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.ArrowUpward,
                        label = stringResource(R.string.home_layout_move_up),
                        enabled = place != null && index > 0,
                        destination = ListItemDestination.Action,
                        onClick = { actions.onMove(type, open.kind, fetcher.name, -1) },
                    ),
                    ListItem(
                        icon = Icons.Outlined.ArrowDownward,
                        label = stringResource(R.string.home_layout_move_down),
                        enabled = place != null && index < place.second - 1,
                        destination = ListItemDestination.Action,
                        onClick = { actions.onMove(type, open.kind, fetcher.name, 1) },
                    ),
                ),
        )
    }
}

private fun MetadataDefaultsUiState.Ready.fetchers(
    type: String,
    kind: FetcherKind,
) = configuration.fetchers(type, kind, offered(type, kind))
