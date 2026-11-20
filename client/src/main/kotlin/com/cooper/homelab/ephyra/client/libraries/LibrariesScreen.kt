package com.cooper.homelab.ephyra.client.libraries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BackdropTile
import com.binge.designsystem.component.BingePullToRefresh
import com.binge.designsystem.component.ScrollToTopOnTabReselect
import com.binge.designsystem.component.SkeletonPlate
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.mediaGridColumns
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ErrorScreen
import com.binge.designsystem.template.ScreenBody
import com.binge.designsystem.template.screenListPadding
import com.binge.designsystem.theme.BingeShapes
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import com.binge.designsystem.R as DesR

/** How many plates the skeleton draws, in the shape of a loaded tile (16:9, as the design system's tile is). */
private const val SKELETON_TILES = 6
private const val TILE_ASPECT_RATIO = 16f / 9f

/** The width a tile's picture is asked for: a phone column at a high density, which a tablet's column is not wider than. One width, so the URL is stable. */
private const val PICTURE_WIDTH_PX = 640

/**
 * The Libraries tab for [state]: the user's movie and series libraries as picture tiles in the design system's grid, so a tablet gets
 * more columns than a phone. [imageUrl] turns a tile and a width in pixels into a URL, or null when the library has no picture, so this
 * screen holds no server and no token. A tile opens its library through [onOpen]. A pull calls [onRefresh]; a failure has its own Try again.
 * [reselect] is the tab the bar shows this under, so a tap on it while it shows scrolls to the top.
 */
@Composable
fun LibrariesScreen(
    state: LibrariesUiState,
    refreshing: Boolean,
    imageUrl: (LibraryTile, Int) -> String?,
    onOpen: (LibraryTile) -> Unit,
    onRefresh: () -> Unit,
    reselect: Any,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    ScrollToTopOnTabReselect(reselect, gridState)
    BingeScreenScaffold(title = stringResource(R.string.client_libraries_title), modifier = modifier) { padding ->
        ScreenBody(padding) { _ ->
            val inner = padding.screenListPadding()
            when (state) {
                LibrariesUiState.Loading -> LibrariesSkeleton(inner)
                LibrariesUiState.Empty ->
                    EmptyScreen(stringResource(R.string.client_libraries_empty), Modifier.fillMaxSize())
                is LibrariesUiState.Error ->
                    ErrorScreen(state.failure.errorKind(), onRefresh.takeIf { state.failure.canRetry() }, Modifier.fillMaxSize())
                is LibrariesUiState.Ready ->
                    BingePullToRefresh(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        indicatorTopInset = padding.calculateTopPadding(),
                    ) {
                        LibrariesGrid(state.tiles, imageUrl, onOpen, gridState, inner)
                    }
            }
        }
    }
}

/** The tiles, [mediaGridColumns] across. */
@Composable
fun LibrariesGrid(
    tiles: List<LibraryTile>,
    imageUrl: (LibraryTile, Int) -> String?,
    onOpen: (LibraryTile) -> Unit,
    state: LazyGridState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    LazyVerticalGrid(
        columns = GridCells.Fixed(mediaGridColumns()),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(tiles, key = { _, tile -> tile.id }) { index, tile ->
            BackdropTile(
                title = tile.name,
                imageUrl = tile.imageTag?.let { imageUrl(tile, PICTURE_WIDTH_PX) },
                onClick = { onOpen(tile) },
                showTitle = tile.showsName,
                modifier = Modifier.fillMaxWidth().firstTileAnchor(index),
            )
        }
    }
}

/** The grid before the libraries have answered: [SKELETON_TILES] plates in the loaded tiles' shape. */
@Composable
fun LibrariesSkeleton(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val gap = dimensionResource(DesR.dimen.padding_m)
    LazyVerticalGrid(
        columns = GridCells.Fixed(mediaGridColumns()),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize(),
    ) {
        items(SKELETON_TILES) { index ->
            SkeletonPlate(Modifier.fillMaxWidth().aspectRatio(TILE_ASPECT_RATIO).firstTileAnchor(index), shape = BingeShapes.MediaCard)
        }
    }
}

/** The anchor of the leading tile, skeleton or loaded, so the geometry test can check the first tile lands where its plate stood. */
internal const val FIRST_TILE_ANCHOR = "libraries-first-tile"

private fun Modifier.firstTileAnchor(index: Int): Modifier =
    if (index == 0) layoutAnchor(LayoutAnchors.section(FIRST_TILE_ANCHOR)) else this
