package com.cooper.homelab.ephyra.client.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.SeasonRow
import com.binge.designsystem.template.DetailInfo
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.MediaDetail
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.PeopleRail
import com.binge.designsystem.template.PosterRail
import com.binge.designsystem.template.SeriesDetail
import com.binge.designsystem.template.SeriesDetailCallbacks
import com.binge.designsystem.template.SeriesDetailScreen
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.Locale
import com.binge.designsystem.R as DesR
import com.binge.designsystem.template.EpisodesState as DesEpisodes

/** The width of the picture behind the title, in pixels. */
private const val HERO_PICTURE_WIDTH = 1080

/** The width of a poster beside the title at the expanded width. */
private const val POSTER_WIDTH = 600

/** How many stats the loading page stands for: a series' usual four (rating, year, seasons, certification). */
private const val SKELETON_STATS = 4

/** How many round actions the loading page stands for: the watched mark and the favourite. */
private const val SKELETON_ACTIONS = 2

/**
 * What the series page does: [onBack] leaves it, [onRetry] reads it again after a failure, [onPlay] is the primary button (it opens nothing
 * yet: in-app play is #756). A season is chosen with [onSelectSeason], the shown season's episodes are read again with [onRetryEpisodes], and
 * [onOpenSeason] opens the season's own page (the button for all its episodes). A tapped episode opens with [onOpenEpisode], and its watched
 * mark is [onToggleEpisodeWatched]. [onOpenTitle] and [onOpenPerson] open a poster card's or a person's page.
 */
class SeriesActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onPlay: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onToggleFavourite: () -> Unit,
    val onSelectSeason: (SeasonUi) -> Unit,
    val onRetryEpisodes: () -> Unit,
    val onOpenSeason: (SeasonUi) -> Unit,
    val onOpenEpisode: (EpisodeUi) -> Unit,
    val onToggleEpisodeWatched: (EpisodeUi) -> Unit,
    val onOpenTitle: (TitleCard) -> Unit,
    val onOpenPerson: (PersonUi) -> Unit,
    val onOpenLink: (url: String) -> Unit,
    val onShare: (title: String, url: String) -> Unit,
)

/**
 * The series page for [state], on the design system's series template. This holds what is a Jellyfin series': the mapping from the state to
 * the template's model (see [seriesDetail]), the Play or Resume button for the next episode as the primary action, the watched and favourite
 * buttons as the round ones, the seasons and the episode rows with their own watched mark. The order of the page, the wide-screen rule, the
 * season picker, the skeleton and the failure frame are the template's. [imageUrl] turns a picture into a URL at a width in pixels, or null
 * when there is none, so this screen holds no server and no token. A write that fails says so in a snackbar, from [events].
 */
@Composable
fun SeriesScreen(
    state: SeriesUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: SeriesActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    events: Flow<TitleEvent> = emptyFlow(),
) {
    val writeFailed = stringResource(R.string.client_title_write_failed)
    LaunchedEffect(events) { events.collect { snackbarHostState.showSnackbar(writeFailed) } }
    val series = (state as? SeriesUiState.Ready)?.series
    // The template hands back the key an item was given, and a key must be a type a list can save, so a key is text and the item is found by it.
    val callbacks =
        MediaDetailCallbacks(
            onBack = actions.onBack,
            onRetry = actions.onRetry,
            onOpenPerson = { key -> series?.people()?.firstOrNull { it.key() == key }?.let(actions.onOpenPerson) },
            onOpenPoster = { key -> series?.similar?.firstOrNull { it.key() == key }?.let(actions.onOpenTitle) },
        )
    val seriesCallbacks =
        SeriesDetailCallbacks(
            onSelectSeason = {},
            onRetryEpisodes = actions.onRetryEpisodes,
            onViewAllEpisodes = { series?.shownSeason()?.let(actions.onOpenSeason) },
            onSelectSeasonKey = { key -> series?.seasons?.firstOrNull { it.id == key }?.let(actions.onSelectSeason) },
            onOpenEpisode = { key -> series?.episodeList()?.firstOrNull { it.id == key }?.let(actions.onOpenEpisode) },
        )
    when (state) {
        SeriesUiState.Loading ->
            SeriesDetailScreen(
                state = DetailState.Loading(stats = SKELETON_STATS, hasPrimary = true, roundActions = SKELETON_ACTIONS),
                series = null,
                callbacks = callbacks,
                seriesCallbacks = seriesCallbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is SeriesUiState.Failed ->
            SeriesDetailScreen(
                // The kind's own words say it: nothing here knows more than why the server could not be read.
                state = DetailState.Failed(retryable = state.failure.canRetry(), kind = state.failure.errorKind()),
                series = null,
                callbacks = callbacks,
                seriesCallbacks = seriesCallbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is SeriesUiState.Ready ->
            SeriesDetailScreen(
                state = DetailState.Content(seriesDetail(state.series, imageUrl)),
                series = seriesEpisodes(state.series, imageUrl, actions),
                callbacks = callbacks,
                seriesCallbacks = seriesCallbacks,
                modifier = modifier,
                primary =
                    state.series.play
                        .primaryLabel()
                        ?.let { label -> { sized -> PlayButton(label, actions.onPlay, sized) } },
                roundActions = seriesRoundActions(state.series, actions),
                afterRails = linksSection(state.series.links, actions.onOpenLink),
                topBarActions = shareAction(state.series.shareUrl) { actions.onShare(state.series.title, it) },
                snackbarHostState = snackbarHostState,
            )
    }
}

@Composable
private fun PlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
) = BingeFilledButton(label = label, onClick = onClick, modifier = modifier, leadingIcon = Icons.Filled.PlayArrow)

/**
 * The page's model for the template, without the episodes: plain display values, every word in the user's language and every picture a URL
 * at the width it is drawn. The cast and the crew are two rails whose items carry the person as their key, and similar series are a poster rail.
 */
@Composable
fun seriesDetail(
    series: SeriesUi,
    imageUrl: (TitleImage, Int) -> String?,
): MediaDetail {
    val locale = LocalConfiguration.current.locales[0]
    return MediaDetail(
        title = series.title,
        backdropUrl = series.backdrop?.let { imageUrl(it, HERO_PICTURE_WIDTH) },
        posterUrl = series.poster?.let { imageUrl(it, POSTER_WIDTH) },
        tagline = series.tagline,
        genres = series.genres,
        stats = series.stats.cells(locale),
        scores = criticsScores(series.criticsPercent),
        overview = series.overview,
        overviewHeading = stringResource(R.string.client_title_overview),
        peopleRails =
            listOf(
                PeopleRail(stringResource(R.string.client_title_cast), peopleItems(series.cast, imageUrl)),
                PeopleRail(stringResource(R.string.client_title_crew), peopleItems(series.crew, imageUrl)),
            ),
        posterRails = listOf(PosterRail(stringResource(R.string.client_title_similar), posterItems(series.similar, imageUrl))),
        info = DetailInfo(stringResource(R.string.client_title_details), series.facts.seriesEntries(locale)),
        tags = tagsSection(series.tags),
    )
}

/**
 * The seasons and the shown season's episodes for the template. Specials sit last, as the server's own order puts them in no one place; a
 * season is keyed by its item id, since two seasons can share a number or have none. An episode's own watched mark is its trailing control.
 */
@Composable
fun seriesEpisodes(
    series: SeriesUi,
    imageUrl: (TitleImage, Int) -> String?,
    actions: SeriesActions,
): SeriesDetail {
    val shown = series.shownSeason()
    return SeriesDetail(
        seasons = series.orderedSeasons().map { it.row() },
        selectedSeason = shown?.number ?: 0,
        selectedSeasonKey = shown?.id,
        episodes =
            when (val episodes = series.episodes) {
                EpisodesState.Loading -> DesEpisodes.Loading
                is EpisodesState.Failed ->
                    DesEpisodes.Failed(stringResource(R.string.client_series_episodes_failed), retryable = episodes.failure.canRetry())
                is EpisodesState.Ready -> DesEpisodes.Ready(episodes.episodes.map { it.row(imageUrl, actions.onToggleEpisodeWatched) })
            },
        episodesTitle = stringResource(R.string.client_series_episodes),
        seasonSubtitle = shown?.let { stringResource(R.string.client_series_season_subtitle, it.name, it.episodeCountText()) },
        viewAllLabel = stringResource(R.string.client_series_all_episodes),
    )
}

/** The season picker's row: its name, with how many episodes it has and how many are not watched. */
@Composable
private fun SeasonUi.row(): SeasonRow =
    SeasonRow(number = number, label = name, subtitle = episodeCountText(withUnwatched = true), key = id)

@Composable
private fun SeasonUi.episodeCountText(withUnwatched: Boolean = false): String {
    val count = episodeCount?.let { pluralStringResource(R.plurals.client_series_episode_count, it, it) }.orEmpty()
    val left = unwatched?.takeIf { withUnwatched && it > 0 && count.isNotEmpty() }
    return if (left != null) stringResource(R.string.client_series_unwatched_count, count, left) else count
}

/** Who is on the page, cast first. */
private fun SeriesUi.people(): List<PersonUi> = cast + crew

/** The seasons with the specials last, the rest in the server's order. */
private fun SeriesUi.orderedSeasons(): List<SeasonUi> = seasons.filterNot { it.isSpecials } + seasons.filter { it.isSpecials }

/** The season whose episodes are shown: the chosen one, else the first. */
private fun SeriesUi.shownSeason(): SeasonUi? = seasons.firstOrNull { it.id == selectedSeasonId } ?: orderedSeasons().firstOrNull()

private fun SeriesUi.episodeList(): List<EpisodeUi> = (episodes as? EpisodesState.Ready)?.episodes.orEmpty()

/** The primary button's words, or null when there is nothing to play: none on the server, or not known yet. A series with every episode watched offers its first again. */
@Composable
internal fun SeriesPlay?.primaryLabel(): String? =
    when (this) {
        is SeriesPlay.Episode -> label()
        is SeriesPlay.AllWatched -> first.label()
        SeriesPlay.NoEpisodes, null -> null
    }

@Composable
private fun SeriesPlay.Episode.label(): String =
    when {
        season == null || episode == null ->
            stringResource(if (kind == PlayKind.Play) R.string.client_title_play else R.string.client_title_resume)
        kind == PlayKind.Play -> stringResource(R.string.client_series_play_episode, season, episode)
        minutesLeft != null -> stringResource(R.string.client_series_resume_episode_left, season, episode, minutesLeft)
        else -> stringResource(R.string.client_series_resume_episode, season, episode)
    }

/** The round buttons beside the primary: watched and favourite, which show their state. */
@Composable
private fun seriesRoundActions(
    series: SeriesUi,
    actions: SeriesActions,
): List<DetailRoundAction> {
    val markWatched = stringResource(if (series.watched) R.string.client_title_mark_unwatched else R.string.client_title_mark_watched)
    val markFavourite =
        stringResource(if (series.favourite) R.string.client_title_remove_favourite else R.string.client_title_add_favourite)
    return listOfNotNull(
        series.trailerUrl?.let { url -> trailerAction { actions.onOpenLink(url) } },
        DetailRoundAction(
            icon = if (series.watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
            label = stringResource(R.string.client_title_watched),
            onClick = actions.onToggleWatched,
            contentDescription = markWatched,
        ),
        DetailRoundAction(
            icon = if (series.favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = stringResource(R.string.client_title_favourite),
            onClick = actions.onToggleFavourite,
            contentDescription = markFavourite,
        ),
    )
}

/** The stats the server gave, each a cell: the rating, the year with its seasons under it, the episodes and the certification. */
@Composable
private fun SeriesStats.cells(locale: Locale): List<DetailStat> =
    listOfNotNull(
        rating?.let { DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(DesR.string.detail_rating)) },
        year?.let { DetailStat(Icons.Filled.CalendarMonth, it.toString(), yearLabel()) },
        episodeCount?.let { DetailStat(Icons.Filled.VideoLibrary, it.toString(), stringResource(R.string.client_series_episodes)) },
        certification?.let { DetailStat(Icons.Filled.Shield, it, stringResource(R.string.client_title_certification)) },
    )

/** What sits under the year: how many seasons the series has, or "Released" when the server counts none. */
@Composable
private fun SeriesStats.yearLabel(): String =
    if (seasonCount > 0) {
        pluralStringResource(R.plurals.client_title_season_count, seasonCount, seasonCount)
    } else {
        stringResource(R.string.client_title_released)
    }
