package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.SortField
import com.cooper.homelab.ephyra.client.libraries.LibraryFilterDraft
import com.cooper.homelab.ephyra.client.libraries.LibraryGridActions
import com.cooper.homelab.ephyra.client.libraries.LibraryGridControls
import com.cooper.homelab.ephyra.client.libraries.LibraryGridScreen
import com.cooper.homelab.ephyra.client.libraries.LibraryGridUiState
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.preview.JellyfinWidePreview

/**
 * A library's grid (`:client`) in each state: loaded on a phone, in Spanish, at larger fonts and on a tablet, with a filter applied,
 * loading, empty (the library's, and a filter's), failed and failing to load more. The frames sit here because the Jellyfin brand and the
 * preview matrices are this module's. No picture is loaded, so every run is the same.
 */
class ClientLibraryGridScreenshotTest {
    private val titles =
        listOf(
            BrowseItem("m1", "Heat", ItemKind.Movie, year = 1995, communityRating = 8.3),
            BrowseItem("s1", "Dark", ItemKind.Series, year = 2017, userData = ItemUserData(unplayedCount = 3)),
            BrowseItem("m2", "Arrival", ItemKind.Movie, year = 2016, userData = ItemUserData(played = true, favourite = true)),
            BrowseItem("m3", "Stalker", ItemKind.Movie, year = 1979, userData = ItemUserData(playedPercentage = 40.0)),
            BrowseItem("s2", "The Wire", ItemKind.Series, year = 2002, userData = ItemUserData(played = true)),
            BrowseItem("m4", "Paterson", ItemKind.Movie, year = 2016),
        )
    private val plain = LibraryGridControls("Movies", LibraryFilterDraft(), null)
    private val filtered =
        LibraryGridControls(
            "Movies",
            LibraryFilterDraft(ItemSort(SortField.Rating, ascending = false), ItemFilters(genres = setOf("Drama"), played = false)),
            null,
        )

    @Composable
    private fun Grid(state: LibraryGridUiState) =
        LibraryGridScreen(
            state = state,
            imageUrl = { _, _ -> null },
            actions = LibraryGridActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinWidePreview
    @Composable
    fun loaded() = Grid(GridState.Ready(plain, titles, hasMore = false, appending = false, appendFailed = false))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun collections() =
        Grid(
            GridState.Ready(
                LibraryGridControls("Collections", LibraryFilterDraft(), null, LibraryKind.BoxSets),
                listOf(
                    BrowseItem("c1", "James Bond Collection", ItemKind.BoxSet, childCount = 24),
                    BrowseItem("c2", "The Matrix Collection", ItemKind.BoxSet, childCount = 4),
                    BrowseItem("c3", "Alien", ItemKind.BoxSet, childCount = 1),
                    BrowseItem("c4", "Studio Ghibli", ItemKind.BoxSet),
                ),
                hasMore = false,
                appending = false,
                appendFailed = false,
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinWidePreview
    @Composable
    fun filterApplied() = Grid(GridState.Ready(filtered, titles.take(3), hasMore = false, appending = false, appendFailed = false))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appending() = Grid(GridState.Ready(plain, titles, hasMore = true, appending = true, appendFailed = false))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appendFailed() = Grid(GridState.Ready(plain, titles, hasMore = true, appending = false, appendFailed = true))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinWidePreview
    @Composable
    fun loading() = Grid(GridState.Loading(plain))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun empty() = Grid(GridState.Empty(plain))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun emptyFiltered() = Grid(GridState.Empty(filtered))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Grid(GridState.Error(plain, ServerFailure.Unreachable()))
}
