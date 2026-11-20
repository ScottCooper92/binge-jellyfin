package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.CollectionActions
import com.cooper.homelab.ephyra.client.title.CollectionPageUi
import com.cooper.homelab.ephyra.client.title.CollectionPart
import com.cooper.homelab.ephyra.client.title.CollectionScreen
import com.cooper.homelab.ephyra.client.title.CollectionUiState
import com.cooper.homelab.ephyra.client.title.TitleCard
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * The collection page (`:client`) in the states the issue names: loaded, all watched, a sparse one, an empty collection, loading, and failed. No picture is loaded, so each frame shows the
 * placeholders and is the same every run.
 */
class ClientCollectionScreenshotTest {
    private fun part(
        id: String,
        title: String,
        year: Int?,
        runtime: Int?,
        played: Boolean = false,
    ) = CollectionPart(TitleCard(id, ItemKind.Movie, title, year, 7.6, TitleImage(id, ImageKind.Primary, "p-$id"), played), runtime)

    private val parts =
        listOf(
            part("a1", "The Lighthouse", 2019, 112, played = true),
            part("a2", "The Lighthouse II: Low Tide", 2021, 121),
            part("a3", "The Lighthouse III: Dead Reckoning", 2023, null),
            part("a4", "The Lighthouse: Origins", null, 45),
            part("a5", "The Lighthouse Remembered", 2025, 98),
        )

    private val collection =
        CollectionPageUi(
            id = "box",
            name = "The Lighthouse Collection",
            overview =
                "Three friends, one lighthouse and a coastline that keeps changing. The films follow what they find there, and what " +
                    "it costs them to leave.",
            backdrop = TitleImage("box", ImageKind.Backdrop, "b-box"),
            poster = TitleImage("box", ImageKind.Primary, "p-box"),
            parts = parts,
            watchedCount = 1,
        )

    @Composable
    private fun Collection(state: CollectionUiState) =
        CollectionScreen(state = state, imageUrl = { _, _ -> null }, actions = CollectionActions({}, {}, {}))

    private fun ready(page: CollectionPageUi) = CollectionUiState.Ready(page)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Collection(ready(collection))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun allWatched() =
        Collection(ready(collection.copy(parts = parts.map { it.copy(card = it.card.copy(played = true)) }, watchedCount = 5)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sparse() =
        Collection(ready(collection.copy(overview = null, backdrop = null, poster = null, parts = parts.take(2), watchedCount = 0)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun empty() = Collection(ready(collection.copy(parts = emptyList(), watchedCount = 0)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Collection(CollectionUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Collection(CollectionUiState.Failed(ServerFailure.Unreachable()))
}
