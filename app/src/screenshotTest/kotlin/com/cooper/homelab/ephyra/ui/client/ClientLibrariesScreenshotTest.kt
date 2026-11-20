package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.client.libraries.LibrariesScreen
import com.cooper.homelab.ephyra.client.libraries.LibrariesUiState
import com.cooper.homelab.ephyra.client.libraries.LibraryTile
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.preview.JellyfinWidePreview

/**
 * The Libraries tab (`:client`) in each state: loaded on a phone, in Spanish, at larger fonts and on a tablet (which draws more
 * columns), loading, empty and failed. The frames sit here because the Jellyfin brand and the preview matrices are this module's. No
 * picture is loaded, so each tile is the plain surface carrying its name, and every run is the same.
 */
class ClientLibrariesScreenshotTest {
    private val tiles =
        listOf(
            LibraryTile("lib-movies", "Movies", LibraryKind.Movies, imageTag = null),
            LibraryTile("lib-shows", "Shows", LibraryKind.TvShows, imageTag = null),
            LibraryTile("lib-kids", "Kids' films", LibraryKind.Movies, imageTag = null),
            LibraryTile("lib-docs", "Documentary series", LibraryKind.TvShows, imageTag = null),
            LibraryTile("lib-classics", "Classic cinema", LibraryKind.Movies, imageTag = null),
        )

    @Composable
    private fun Libraries(state: LibrariesUiState) =
        LibrariesScreen(state, refreshing = false, imageUrl = { _, _ -> null }, onOpen = {}, onRefresh = {}, reselect = "libraries")

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinWidePreview
    @Composable
    fun loaded() = Libraries(LibrariesUiState.Ready(tiles))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinWidePreview
    @Composable
    fun loading() = Libraries(LibrariesUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = Libraries(LibrariesUiState.Empty)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Libraries(LibrariesUiState.Error(ServerFailure.Unreachable()))
}
