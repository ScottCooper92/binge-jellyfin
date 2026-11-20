package com.cooper.homelab.ephyra.client.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.SeasonRow
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.MediaDetail
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.SeriesDetail
import com.binge.designsystem.template.SeriesDetailCallbacks
import com.binge.designsystem.template.SeriesDetailScreen
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.binge.designsystem.template.EpisodesState as DesEpisodes

/** The width of a season's poster beside the title at the expanded width, in pixels. */
private const val POSTER_WIDTH = 600

/** How many stats the loading page stands for: the episode count. */
private const val SKELETON_STATS = 1

/** How many round actions the loading page stands for: the watched mark and the way to the series. */
private const val SKELETON_ACTIONS = 2

/**
 * What the season page does: [onBack] leaves it, [onRetry] reads the season again after a failure, [onPlay] is the primary button (it opens
 * nothing yet: in-app play is #756) and [onRetryEpisodes] reads its episodes again. A tapped episode opens with [onOpenEpisode] and its watched
 * mark is [onToggleEpisodeWatched]; [onToggleWatched] marks the whole season, and [onOpenSeries] opens the series it belongs to.
 */
class SeasonActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onPlay: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onRetryEpisodes: () -> Unit,
    val onOpenEpisode: (EpisodeUi) -> Unit,
    val onToggleEpisodeWatched: (EpisodeUi) -> Unit,
    val onOpenSeries: (seriesId: String) -> Unit,
)

/**
 * The season page for [state], on the design system's series template, which lists every episode of the season (the template's `shown`
 * left wide enough for all of them) under a one-season header, so it draws no season picker. This holds what is a Jellyfin season's: the
 * mapping to the template's model (see [seasonDetail]), the Play or Resume button for the next episode as the primary action, the watched
 * and series buttons as the round ones, and each episode row with its own watched toggle. [imageUrl] turns a picture into a URL at a width in
 * pixels, or null when there is none, so this screen holds no server and no token. A write that fails says so in a snackbar, from [events].
 */
@Composable
fun SeasonScreen(
    state: SeasonUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: SeasonActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    events: Flow<TitleEvent> = emptyFlow(),
) {
    val writeFailed = stringResource(R.string.client_title_write_failed)
    LaunchedEffect(events) { events.collect { snackbarHostState.showSnackbar(writeFailed) } }
    val callbacks = MediaDetailCallbacks(onBack = actions.onBack, onRetry = actions.onRetry)
    val season = (state as? SeasonUiState.Ready)?.season
    val seriesCallbacks =
        SeriesDetailCallbacks(
            onSelectSeason = {},
            onRetryEpisodes = actions.onRetryEpisodes,
            onOpenEpisode = { key -> season?.episodeList()?.firstOrNull { it.id == key }?.let(actions.onOpenEpisode) },
        )
    when (state) {
        SeasonUiState.Loading ->
            SeriesDetailScreen(
                state = DetailState.Loading(stats = SKELETON_STATS, hasPrimary = true, roundActions = SKELETON_ACTIONS),
                series = null,
                callbacks = callbacks,
                seriesCallbacks = seriesCallbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is SeasonUiState.Failed ->
            SeriesDetailScreen(
                state = DetailState.Failed(retryable = state.failure.canRetry(), kind = state.failure.errorKind()),
                series = null,
                callbacks = callbacks,
                seriesCallbacks = seriesCallbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is SeasonUiState.Ready ->
            SeriesDetailScreen(
                state = DetailState.Content(seasonDetail(state.season, imageUrl)),
                series = seasonEpisodes(state.season, imageUrl, actions),
                callbacks = callbacks,
                seriesCallbacks = seriesCallbacks,
                modifier = modifier,
                primary =
                    state.season.play
                        .primaryLabel()
                        ?.let { label -> { sized -> PlayButton(label, actions.onPlay, sized) } },
                roundActions = seasonRoundActions(state.season, actions),
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
 * The page's model for the template, without the episodes: the season's name and its poster, the series' name under the title, the episode
 * count as the one stat, and the overview. A season has no cast, no similar rail and no details of its own, so the template leaves them out.
 */
@Composable
fun seasonDetail(
    season: SeasonPageUi,
    imageUrl: (TitleImage, Int) -> String?,
): MediaDetail =
    MediaDetail(
        title = season.name,
        posterUrl = season.poster?.let { imageUrl(it, POSTER_WIDTH) },
        tagline = season.seriesName,
        stats =
            season
                .episodeCount()
                ?.let {
                    listOf(DetailStat(Icons.Filled.VideoLibrary, it.toString(), stringResource(R.string.client_series_episodes)))
                }.orEmpty(),
        overview = season.overview,
        overviewHeading = stringResource(R.string.client_title_overview),
    )

/**
 * The season's episodes for the template: the one season as a one-row picker (so there is no dropdown), every episode listed, and each row
 * with its watched toggle. The count line under the heading is the season's name and how many episodes it has.
 */
@Composable
fun seasonEpisodes(
    season: SeasonPageUi,
    imageUrl: (TitleImage, Int) -> String?,
    actions: SeasonActions,
): SeriesDetail {
    val count = season.episodeCount()?.let { pluralStringResource(R.plurals.client_series_episode_count, it, it) }.orEmpty()
    return SeriesDetail(
        seasons = listOf(SeasonRow(number = season.seasonNumber, label = season.name, subtitle = count, key = season.id)),
        selectedSeason = season.seasonNumber,
        selectedSeasonKey = season.id,
        episodes =
            when (val episodes = season.episodes) {
                EpisodesState.Loading -> DesEpisodes.Loading
                is EpisodesState.Failed ->
                    DesEpisodes.Failed(stringResource(R.string.client_series_episodes_failed), retryable = episodes.failure.canRetry())
                is EpisodesState.Ready -> DesEpisodes.Ready(episodes.episodes.map { it.row(imageUrl, actions.onToggleEpisodeWatched) })
            },
        episodesTitle = stringResource(R.string.client_series_episodes),
        seasonSubtitle = count.ifEmpty { null },
        shown = Int.MAX_VALUE,
    )
}

private fun SeasonPageUi.episodeList(): List<EpisodeUi> = (episodes as? EpisodesState.Ready)?.episodes.orEmpty()

/** How many episodes the season has, once they are read. */
private fun SeasonPageUi.episodeCount(): Int? = (episodes as? EpisodesState.Ready)?.episodes?.size

/** The round buttons beside the primary: watched, which shows its state, and the series the season belongs to. */
@Composable
private fun seasonRoundActions(
    season: SeasonPageUi,
    actions: SeasonActions,
): List<DetailRoundAction> {
    val markWatched = stringResource(if (season.watched) R.string.client_title_mark_unwatched else R.string.client_title_mark_watched)
    return buildList {
        add(
            DetailRoundAction(
                icon = if (season.watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                label = stringResource(R.string.client_title_watched),
                onClick = actions.onToggleWatched,
                contentDescription = markWatched,
            ),
        )
        season.seriesId?.let { seriesId ->
            add(
                DetailRoundAction(
                    icon = Icons.Filled.Tv,
                    label = stringResource(R.string.client_title_series),
                    onClick = { actions.onOpenSeries(seriesId) },
                    contentDescription = stringResource(R.string.client_title_go_to_series),
                ),
            )
        }
    }
}
