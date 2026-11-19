package com.cooper.homelab.ephyra.client.home

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.LibraryView

private const val PERCENT = 100f

/**
 * The rows Home wants, in order: Continue watching, Next up, then the latest of each library that holds movies or shows. A library of
 * another kind (box sets, home videos) has no row of its own on Home.
 */
fun homeRowKeys(libraries: List<LibraryView>): List<HomeRowKey> =
    listOf(HomeRowKey.ContinueWatching, HomeRowKey.NextUp) +
        libraries
            .filter { it.kind == LibraryKind.Movies || it.kind == LibraryKind.TvShows }
            .map { HomeRowKey.Latest(it.id, it.name) }

/** The row for [key] from what the server answered, or null when it has nothing to show, so the row is left out. */
fun homeRow(
    key: HomeRowKey,
    items: List<BrowseItem>,
): HomeRow? = items.map { it.toHomeCard(key) }.takeIf { it.isNotEmpty() }?.let { HomeRow(key, it) }

/** [this] as a card of the row [key]. */
fun BrowseItem.toHomeCard(key: HomeRowKey): HomeCard {
    val episode = kind == ItemKind.Episode
    val year = year
    return HomeCard(
        id = id,
        kind = kind,
        title = if (episode) seriesName ?: name else name,
        caption =
            when {
                episode -> HomeCaption.Episode(seasonNumber, episodeNumber, name)
                year != null -> HomeCaption.Year(year)
                else -> HomeCaption.None
            },
        poster = poster(),
        backdrop = backdrop(),
        progress = progress(),
        rating = communityRating,
        resumes = key == HomeRowKey.ContinueWatching,
    )
}

/** An episode shows its series' poster, as the server's own clients do: its own picture is a still, not a poster. */
private fun BrowseItem.poster(): HomeImage? {
    val series = seriesId
    val seriesTag = seriesPrimaryImageTag
    return if (kind == ItemKind.Episode && series != null && seriesTag != null) {
        HomeImage(series, ImageKind.Primary, seriesTag)
    } else {
        imageTags[ImageKind.Primary]?.let { HomeImage(id, ImageKind.Primary, it) }
    }
}

/** The item's own backdrop, else its parent's (an episode has its series'), else none. */
private fun BrowseItem.backdrop(): HomeImage? {
    val parent = parentBackdropItemId
    val parentTag = parentBackdropImageTag
    return imageTags[ImageKind.Backdrop]?.let { HomeImage(id, ImageKind.Backdrop, it) }
        ?: if (parent != null && parentTag != null) HomeImage(parent, ImageKind.Backdrop, parentTag) else null
}

/** How far through the item the user is, 0 to 1, from the server's own percentage or else from the position; null when there is none to show. */
private fun BrowseItem.progress(): Float? {
    if (userData.played) return null
    val fraction =
        userData.playedPercentage?.let { (it / PERCENT).toFloat() }
            ?: runTimeTicks?.takeIf { it > 0 }?.let { userData.positionTicks.toFloat() / it }
    return fraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }
}

/**
 * The hero: the first Continue watching card that has a backdrop, else the first card of the latest rows that has one, else none, so
 * a server with no backdrops has no hero rather than an empty one.
 */
fun pickHero(rows: List<HomeRow>): HomeHero? {
    fun first(of: List<HomeRow>) =
        of.firstNotNullOfOrNull { row -> row.cards.firstNotNullOfOrNull { card -> card.backdrop?.let { HomeHero(card, it) } } }
    return first(rows.filter { it.key == HomeRowKey.ContinueWatching })
        ?: first(rows.filter { it.key is HomeRowKey.Latest })
}

/**
 * What Home shows from the rows that have something to show and how many are still loading: nothing yet is [HomeUiState.Loading], and
 * every row settled with nothing is [HomeUiState.Empty].
 */
fun homeState(
    rows: List<HomeRow>,
    pending: Int,
): HomeUiState =
    when {
        rows.isNotEmpty() -> HomeUiState.Ready(pickHero(rows), rows, pending)
        pending > 0 -> HomeUiState.Loading
        else -> HomeUiState.Empty
    }
