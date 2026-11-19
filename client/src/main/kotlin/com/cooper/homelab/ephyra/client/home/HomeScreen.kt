package com.cooper.homelab.ephyra.client.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.HeroCarousel
import com.binge.designsystem.component.HeroItem
import com.binge.designsystem.component.MediaCard
import com.binge.designsystem.component.MediaCarousel
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.EmptyScreen
import com.binge.designsystem.template.ErrorScreen
import com.cooper.homelab.ephyra.client.R
import com.binge.designsystem.R as DesR

/** The width of the hero's picture asked of the server: a phone's width, which a backdrop is drawn across. */
private const val HERO_PICTURE_WIDTH = 1080

/**
 * Home for [state]. [imageUrl] turns a picture into a URL at a width in pixels, or null when there is none, so this screen holds no
 * server and no token. A card opens its page through [onOpen]; a Continue watching card, and the hero when it is one, calls [onResume].
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    imageUrl: (HomeImage, Int) -> String?,
    onOpen: (HomeCard) -> Unit,
    onResume: (HomeCard) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    when (state) {
        HomeUiState.Loading -> HomeSkeleton(modifier, contentPadding)
        HomeUiState.Empty -> EmptyScreen(stringResource(R.string.home_empty), modifier.padding(contentPadding))
        is HomeUiState.Error ->
            ErrorScreen(state.failure.errorKind(), onRetry.takeIf { state.failure.canRetry() }, modifier.padding(contentPadding))
        is HomeUiState.Ready -> HomeContent(state, imageUrl, onOpen, onResume, modifier, contentPadding)
    }
}

/** The rows that have landed, with a skeleton for each still loading below them, so a row that arrives later moves nothing. */
@Composable
fun HomeContent(
    state: HomeUiState.Ready,
    imageUrl: (HomeImage, Int) -> String?,
    onOpen: (HomeCard) -> Unit,
    onResume: (HomeCard) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val cardWidth = dimensionResource(DesR.dimen.card_width)
    val cardWidthPx = with(LocalDensity.current) { cardWidth.roundToPx() }
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        state.hero?.let { hero ->
            item(key = HERO_KEY) {
                HeroCarousel(
                    items = listOf(hero.toHeroItem(imageUrl(hero.backdrop, HERO_PICTURE_WIDTH))),
                    onItemClick = { if (hero.card.resumes) onResume(hero.card) else onOpen(hero.card) },
                    modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.HERO)),
                )
            }
        }
        itemsIndexed(state.rows, key = { _, row -> row.key.rowId() }) { index, row ->
            MediaCarousel(
                title = row.key.title(),
                items = row.cards,
                itemKey = { it.id },
                modifier = Modifier.firstRowAnchor(index),
            ) { _, card ->
                MediaCard(
                    posterUrl = card.poster?.let { imageUrl(it, cardWidthPx) },
                    title = card.title,
                    rating = card.rating?.toFloat(),
                    onClick = { if (card.resumes) onResume(card) else onOpen(card) },
                    modifier = Modifier.width(cardWidth),
                    progress = card.progress,
                    subtitle = card.caption.text(),
                )
            }
        }
        items(state.pending) { CarouselSkeleton(description = stringResource(R.string.home_loading_row)) }
    }
}

/** The page before any row has answered: the hero's place and [SKELETON_ROWS] rows, the first on the anchor the loaded first row takes. */
@Composable
fun HomeSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(modifier = modifier, contentPadding = contentPadding, userScrollEnabled = false) {
        item {
            HeroCarousel(
                items = emptyList(),
                onItemClick = {},
                modifier = Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.HERO)),
            )
        }
        items(SKELETON_ROWS) { index ->
            CarouselSkeleton(modifier = Modifier.firstRowAnchor(index), description = stringResource(R.string.home_loading_row))
        }
    }
}

/** Marks the leading row, skeleton or loaded, so the geometry test can check the first row lands where its plate stood. */
private fun Modifier.firstRowAnchor(index: Int): Modifier =
    if (index == 0) layoutAnchor(LayoutAnchors.section(LayoutAnchors.Hub.FIRST_CAROUSEL)) else this

private fun HomeHero.toHeroItem(imageUrl: String?): HeroItem =
    HeroItem(
        id = 0,
        imageUrl = imageUrl,
        title = card.title,
        rating = card.rating?.toFloat(),
        year = (card.caption as? HomeCaption.Year)?.year?.toString(),
    )

@Composable
private fun HomeRowKey.title(): String =
    when (this) {
        HomeRowKey.ContinueWatching -> stringResource(R.string.home_row_continue_watching)
        HomeRowKey.NextUp -> stringResource(R.string.home_row_next_up)
        is HomeRowKey.Latest -> stringResource(R.string.home_row_latest, libraryName)
    }

/** The caption in words, or null when the card has none. */
@Composable
private fun HomeCaption.text(): String? =
    when (this) {
        HomeCaption.None -> null
        is HomeCaption.Year -> year.toString()
        is HomeCaption.Episode ->
            if (season != null && episode != null) stringResource(R.string.home_caption_episode, season, episode, name) else name
    }

private fun HomeRowKey.rowId(): String =
    when (this) {
        HomeRowKey.ContinueWatching -> "continue"
        HomeRowKey.NextUp -> "next-up"
        is HomeRowKey.Latest -> "latest-$libraryId"
    }

private const val HERO_KEY = "hero"

/** How many rows the skeleton stands for: the two personal rows and a library's latest, the usual first screenful. */
private const val SKELETON_ROWS = 3
