package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.client.home.HomeRowKey
import com.cooper.homelab.ephyra.client.home.HomeScreen
import com.cooper.homelab.ephyra.client.home.HomeUiState
import com.cooper.homelab.ephyra.client.home.homeRow
import com.cooper.homelab.ephyra.client.home.homeState
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.server.TICKS_PER_HOUR

/**
 * Home (`:client`) in the states the issue names: loaded, loading, partly failed (a row still loading under the rows that landed) and
 * every row failed, with the empty one. The frames sit here because the Jellyfin brand and the preview matrices are this module's;
 * the screen is `:client`'s. No picture is loaded, so each frame shows the placeholders and is the same every run.
 */
class ClientHomeScreenshotTest {
    private fun movie(
        id: String,
        name: String,
        year: Int,
        progress: Double? = null,
    ) = BrowseItem(
        id = id,
        name = name,
        kind = ItemKind.Movie,
        year = year,
        communityRating = 7.8,
        runTimeTicks = 2 * TICKS_PER_HOUR,
        imageTags = mapOf(ImageKind.Primary to "p-$id", ImageKind.Backdrop to "b-$id"),
        userData = ItemUserData(playedPercentage = progress),
    )

    private fun episode(
        id: String,
        series: String,
        number: Int,
    ) = BrowseItem(
        id = id,
        name = "Episode $number",
        kind = ItemKind.Episode,
        seriesId = "series-$series",
        seriesName = series,
        seriesPrimaryImageTag = "sp-$series",
        seasonNumber = 1,
        episodeNumber = number,
    )

    private val resume =
        homeRow(
            HomeRowKey.ContinueWatching,
            listOf(movie("m1", "The Long Quiet", 2021, progress = 40.0), episode("e1", "Harbour Lights", 3)),
        )!!
    private val nextUp = homeRow(HomeRowKey.NextUp, listOf(episode("e2", "Second Shift", 2), episode("e3", "Harbour Lights", 4)))!!
    private val latest =
        homeRow(
            HomeRowKey.Latest("lib-movies", "Movies"),
            listOf(movie("m2", "Night Market", 2019), movie("m3", "Paper Boats", 2023), movie("m4", "Salt Road", 2017)),
        )!!

    @Composable
    private fun Home(state: HomeUiState) = HomeScreen(state, imageUrl = { _, _ -> null }, onOpen = {}, onResume = {}, onRetry = {})

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Home(homeState(listOf(resume, nextUp, latest), pending = 0))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Home(HomeUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun partlyFailed() = Home(homeState(listOf(latest), pending = 1))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun allFailed() = Home(HomeUiState.Error(ServerFailure.Unreachable()))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = Home(HomeUiState.Empty)
}
