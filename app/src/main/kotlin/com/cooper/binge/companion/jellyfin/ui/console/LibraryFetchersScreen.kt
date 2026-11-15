package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeAdaptiveSheet
import com.binge.designsystem.component.BingeSheetKind
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.PageIntro
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.FetcherType
import com.cooper.binge.companion.jellyfin.server.ImageSlot
import com.cooper.binge.companion.jellyfin.server.fetchers
import com.cooper.binge.companion.jellyfin.server.imageOptions
import com.binge.designsystem.R as DesR

/** The words for a kind of fetcher. */
internal fun FetcherKind.title(): Int =
    when (this) {
        FetcherKind.Metadata -> R.string.library_metadata_fetchers
        FetcherKind.Images -> R.string.library_image_fetchers
    }

/** The words for an item type the server names, or the name itself for one this app has no words for. */
@Composable
internal fun typeLabel(type: String): String =
    when (type) {
        "Movie" -> stringResource(R.string.library_type_movie)
        "Series" -> stringResource(R.string.library_type_series)
        "Season" -> stringResource(R.string.library_type_season)
        "Episode" -> stringResource(R.string.library_type_episode)
        "BoxSet" -> stringResource(R.string.library_type_boxset)
        "MusicArtist" -> stringResource(R.string.library_type_artist)
        "MusicAlbum" -> stringResource(R.string.library_type_album)
        "Audio" -> stringResource(R.string.library_type_song)
        "MusicVideo" -> stringResource(R.string.library_type_music_video)
        "Video" -> stringResource(R.string.library_type_video)
        "Book" -> stringResource(R.string.library_type_book)
        "AudioBook" -> stringResource(R.string.library_type_audiobook)
        "Studio" -> stringResource(R.string.library_type_studio)
        else -> type
    }

/**
 * Each item type the library holds, with the [kind] fetchers it asks in order, each opening its own page. A type the server
 * offers no fetchers for is left out. Nothing here is written until a fetcher is changed.
 */
@Composable
internal fun FetchersPage(
    kind: FetcherKind,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        PageIntro(stringResource(R.string.library_fetchers_body))
        state.available.types.forEach { type ->
            val all = state.options.fetchers(type, kind)
            if (all.isNotEmpty()) {
                ItemGroup(
                    title = typeLabel(type.type),
                    rows =
                        all.map { fetcher ->
                            ListItem(
                                icon = Icons.Outlined.Extension,
                                label = fetcher.name,
                                detail =
                                    stringResource(
                                        if (fetcher.enabled) R.string.library_provider_on else R.string.library_provider_off,
                                    ),
                                onClick = { actions.onPage(LibraryPage.Fetcher(kind, type.type, fetcher.name)) },
                            )
                        },
                )
            }
            if (kind == FetcherKind.Images) ImageOptionsGroup(type, state, actions)
        }
    }
}

/** A type's image types, each with how many images are kept and the narrowest accepted, opening the sheet that sets them. */
@Composable
private fun ImageOptionsGroup(
    type: FetcherType,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
) {
    val slots = state.options.imageOptions(type)
    if (slots.isNotEmpty()) {
        ItemGroup(
            title = stringResource(R.string.library_image_options, typeLabel(type.type)),
            rows =
                slots.map { slot ->
                    ListItem(
                        icon = Icons.Outlined.PhotoSizeSelectLarge,
                        label = slot.type,
                        detail = slot.detail(),
                        onClick = { actions.onAsk(LibraryDialog.ImageOptions(type.type, slot.type)) },
                    )
                },
        )
    }
}

@Composable
private fun ImageSlot.detail(): String =
    if (limit == 0) {
        stringResource(R.string.library_image_none)
    } else {
        pluralStringResource(R.plurals.library_image_detail, limit, limit, minWidth)
    }

/** The sheet for one image type of an item type: how many images to keep, and the narrowest width accepted. Each saves as it lands. */
@Composable
internal fun ImageOptionsSheet(
    dialog: LibraryDialog.ImageOptions,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
) {
    val type = state.available.types.firstOrNull { it.type == dialog.type }
    val slot = type?.let { state.options.imageOptions(it) }?.firstOrNull { it.type == dialog.image }
    if (slot != null) {
        BingeAdaptiveSheet(kind = BingeSheetKind.Task, onDismissRequest = { actions.onAsk(null) }) {
            ImageOptionsContent(slot) { actions.onImage(dialog.type, it) }
        }
    }
}

/** The sheet's body, which draws without a window for a frame. */
@Composable
internal fun ImageOptionsContent(
    slot: ImageSlot,
    onSave: (ImageSlot) -> Unit,
) {
    val resources = LocalContext.current.resources
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l))) {
        NumberSliderContent(
            title = stringResource(R.string.library_image_limit, slot.type),
            value = slot.limit,
            range = 0..IMAGE_LIMIT_MAX,
            format = { it.toString() },
            openEndLabel = null,
            onSave = { onSave(slot.copy(limit = it ?: slot.limit)) },
        )
        NumberSliderContent(
            title = stringResource(R.string.library_image_min_width, slot.type),
            value = slot.minWidth,
            range = 0..IMAGE_WIDTH_MAX,
            format = { resources.getString(R.string.library_image_px, it) },
            openEndLabel = null,
            onSave = { onSave(slot.copy(minWidth = it ?: slot.minWidth)) },
            step = IMAGE_WIDTH_STEP,
        )
    }
}

private const val IMAGE_LIMIT_MAX = 20
private const val IMAGE_WIDTH_MAX = 4000
private const val IMAGE_WIDTH_STEP = 20

/** One fetcher of one type: whether the library uses it, and its place in that type's order. */
@Composable
internal fun FetcherPage(
    page: LibraryPage.Fetcher,
    state: LibraryUiState.Ready,
    actions: LibraryActions,
) {
    val type: FetcherType? = state.available.types.firstOrNull { it.type == page.type }
    val all = type?.let { state.options.fetchers(it, page.kind) }.orEmpty()
    val place = all.indexOfFirst { it.name.equals(page.name, ignoreCase = true) }
    val fetcher = all.getOrNull(place)
    if (fetcher != null) {
        OrderedSettingsPage(
            title = typeLabel(page.type),
            switches =
                listOf(
                    ListItem(
                        icon = Icons.Outlined.PowerSettingsNew,
                        label = stringResource(R.string.library_provider_use),
                        toggled = fetcher.enabled,
                        onClick = { actions.onFetcher(page.kind, page.type, fetcher.name, !fetcher.enabled) },
                    ),
                ),
            place = place,
            size = all.size,
            onMove = { actions.onMoveFetcher(page.kind, page.type, fetcher.name, it) },
        )
    }
}
