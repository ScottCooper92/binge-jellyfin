package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.PersonResult
import com.cooper.homelab.ephyra.client.search.SearchActions
import com.cooper.homelab.ephyra.client.search.SearchBody
import com.cooper.homelab.ephyra.client.search.SearchHit
import com.cooper.homelab.ephyra.client.search.SearchTabScreen
import com.cooper.homelab.ephyra.client.search.SearchType
import com.cooper.homelab.ephyra.client.search.SearchUiState
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.preview.JellyfinWidePreview

/**
 * The Search tab (`:client`) in each state: the recent queries, loading, results of every kind with their marks, narrowed to one type,
 * more on the way and failing, nothing found, and a failed search. No picture is loaded, so every run is the same.
 */
class ClientSearchScreenshotTest {
    private val hits =
        listOf(
            SearchHit.Title(BrowseItem("m1", "Heat", ItemKind.Movie, year = 1995, userData = ItemUserData(played = true))),
            SearchHit.Title(BrowseItem("s1", "Heat Lightning", ItemKind.Series, year = 2017, userData = ItemUserData(favourite = true))),
            SearchHit.Title(
                BrowseItem(
                    "e1",
                    "The Heat of the Night",
                    ItemKind.Episode,
                    seriesName = "Dark",
                    seasonNumber = 1,
                    episodeNumber = 4,
                ),
            ),
            SearchHit.Person(PersonResult("p1", "Heather Graham", null)),
        )

    private fun ready(
        list: List<SearchHit> = hits,
        hasMore: Boolean = false,
        appending: Boolean = false,
        appendFailed: Boolean = false,
    ) = SearchBody.Ready(list, loaded = list.size, hasMore = hasMore, appending = appending, appendFailed = appendFailed)

    @Composable
    private fun Search(
        body: SearchBody,
        text: String = "heat",
        type: SearchType = SearchType.All,
    ) = SearchTabScreen(
        state = SearchUiState(text, type, body),
        imageUrl = { _, _ -> null },
        actions = SearchActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}),
    )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinWidePreview
    @Composable
    fun results() = Search(ready())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun recents() = Search(SearchBody.Idle(listOf("alien", "heat", "the wire")), text = "")

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun idleWithNoRecents() = Search(SearchBody.Idle(emptyList()), text = "")

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Search(SearchBody.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun people() = Search(ready(hits.filterIsInstance<SearchHit.Person>()), type = SearchType.People)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appending() = Search(ready(hasMore = true, appending = true))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appendFailed() = Search(ready(hasMore = true, appendFailed = true))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun noResults() = Search(SearchBody.NoResults("heat"))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Search(SearchBody.Failed(ServerFailure.Unreachable()))
}
