package com.cooper.homelab.ephyra.client.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.DetailEpisodesSection
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.EpisodeListRowSkeleton
import com.binge.designsystem.template.DetailInfo
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.MediaDetail
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.MovieDetailScreen
import com.binge.designsystem.template.PeopleRail
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.Locale
import com.binge.designsystem.R as DesR

/** The width of the picture behind the title, in pixels: the episode's still, or its series' backdrop. */
private const val HERO_PICTURE_WIDTH = 1080

/** How many stats the loading page stands for: an episode's usual three (rating, runtime, certification). */
private const val SKELETON_STATS = 3

/** How many round actions the loading page stands for: the watched mark and the favourite, the two every episode has. */
private const val SKELETON_ACTIONS = 2

/** How many placeholder rows the season's episodes show while they load. */
private const val SKELETON_ROWS = 2

/** The season's other episodes are all shown: the page has no other place to list them. */
private const val ALL_EPISODES = Int.MAX_VALUE

/**
 * What the episode page does: [onBack] leaves it, [onRetry] reads the episode again after a failure, and [onPlay] is the primary button,
 * which opens nothing yet (in-app play is #756). [onRetrySeason] reads the season's other episodes again, and [onOpenEpisode] opens one.
 * [onOpenPerson] opens a person's page, and [onOpenSeries] the series the episode belongs to, which an episode reached from Home has no other way to.
 */
class EpisodeActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onPlay: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onToggleFavourite: () -> Unit,
    val onRetrySeason: () -> Unit,
    val onOpenEpisode: (EpisodeUi) -> Unit,
    val onOpenPerson: (PersonUi) -> Unit,
    val onOpenSeries: (seriesId: String) -> Unit,
)

/**
 * The episode page for [state], on the design system's movie template, which an episode is closest to. This holds what is an episode's: the
 * mapping from the state to the template's model (see [episodeDetail]), the Play or Resume button as the primary action, the watched,
 * favourite and versions buttons as the round ones, the file's media information at the end, and the season's other episodes after the
 * people, in the template's own episode rows. The order of the page, the wide-screen rule, the skeleton and the failure frame are the
 * template's. [imageUrl] turns a picture into a URL at a width in pixels, or null when there is none, so this screen holds no server and no
 * token. A write that fails says so in a snackbar, from [events].
 */
@Composable
fun EpisodeScreen(
    state: EpisodeUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: EpisodeActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    events: Flow<TitleEvent> = emptyFlow(),
) {
    val writeFailed = stringResource(R.string.client_title_write_failed)
    LaunchedEffect(events) { events.collect { snackbarHostState.showSnackbar(writeFailed) } }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var version by rememberSaveable { mutableStateOf<String?>(null) }
    val episode = (state as? EpisodeUiState.Ready)?.episode
    val callbacks =
        MediaDetailCallbacks(
            onBack = actions.onBack,
            onRetry = actions.onRetry,
            onOpenPerson = { key -> episode?.people()?.firstOrNull { it.key() == key }?.let(actions.onOpenPerson) },
        )
    when (state) {
        EpisodeUiState.Loading ->
            MovieDetailScreen(
                state = DetailState.Loading(stats = SKELETON_STATS, hasPrimary = true, roundActions = SKELETON_ACTIONS),
                callbacks = callbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is EpisodeUiState.Failed ->
            MovieDetailScreen(
                // The kind's own words say it: nothing here knows more than why the server could not be read.
                state = DetailState.Failed(retryable = state.failure.canRetry(), kind = state.failure.errorKind()),
                callbacks = callbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is EpisodeUiState.Ready ->
            MovieDetailScreen(
                state = DetailState.Content(episodeDetail(state.episode, imageUrl)),
                callbacks = callbacks,
                modifier = modifier,
                primary = { sized ->
                    BingeFilledButton(
                        label = state.episode.play.label(),
                        onClick = actions.onPlay,
                        modifier = sized,
                        leadingIcon = Icons.Filled.PlayArrow,
                    )
                },
                roundActions = episodeRoundActions(state.episode, actions, onChooseVersion = { choosing = true }),
                afterPeople = state.episode.season?.let { season -> { SeasonEpisodes(state.episode, season, imageUrl, actions) } },
                atEnd = {
                    val shown = state.episode.versions.firstOrNull { it.id == version } ?: state.episode.versions.firstOrNull()
                    shown?.let { MovieMediaInfo(it) }
                },
                overlays = {
                    if (choosing) {
                        VersionSheet(
                            versions = state.episode.versions,
                            selected =
                                version ?: state.episode.versions
                                    .firstOrNull()
                                    ?.id,
                            onSelect = {
                                version = it
                                choosing = false
                            },
                            onDismiss = { choosing = false },
                        )
                    }
                },
                snackbarHostState = snackbarHostState,
            )
    }
}

/**
 * The page's model for the template: plain display values, every word in the user's language and every picture a URL at the width it is
 * drawn. The tagline names the series and where the episode sits in it; the guest cast and the crew are two rails whose items carry the
 * person as their key.
 */
@Composable
fun episodeDetail(
    episode: EpisodePageUi,
    imageUrl: (TitleImage, Int) -> String?,
): MediaDetail {
    val locale = LocalConfiguration.current.locales[0]
    return MediaDetail(
        title = episode.title,
        backdropUrl = episode.still?.let { imageUrl(it, HERO_PICTURE_WIDTH) },
        tagline = episode.position(),
        stats = episode.stats.cells(locale),
        overview = episode.overview,
        overviewHeading = stringResource(R.string.client_title_overview),
        peopleRails =
            listOf(
                PeopleRail(stringResource(R.string.client_title_guest_cast), peopleItems(episode.guestCast, imageUrl)),
                PeopleRail(stringResource(R.string.client_title_crew), peopleItems(episode.crew, imageUrl)),
            ),
        info = DetailInfo(stringResource(R.string.client_title_details), listOf(airDateEntry(episode.airDate, locale))),
    )
}

/** The series' name with the season and episode, such as "The Lighthouse Keepers · S1 · E3"; each part the server did not give is left out. */
@Composable
private fun EpisodePageUi.position(): String? {
    val place =
        if (seasonNumber != null && episodeNumber != null) {
            stringResource(R.string.client_title_season_episode, seasonNumber, episodeNumber)
        } else {
            null
        }
    return listOfNotNull(seriesName, place).joinToString(" · ").ifBlank { null }
}

@Composable
private fun airDateEntry(
    airDate: String?,
    locale: Locale,
): DetailInfoEntry =
    DetailInfoEntry(
        Icons.Filled.Event,
        stringResource(R.string.client_title_air_date),
        airDate?.let {
            premiere(it, locale)
        },
    )

/** The season's other episodes after the people: the template's rows, a placeholder while they load, or the message with Try again. */
@Composable
private fun SeasonEpisodes(
    episode: EpisodePageUi,
    season: EpisodesState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: EpisodeActions,
) {
    val others = (season as? EpisodesState.Ready)?.episodes.orEmpty().filter { it.id != episode.id }
    if (season is EpisodesState.Ready && others.isEmpty()) return
    DetailEpisodesSection(
        title =
            episode.seasonNumber?.let { stringResource(R.string.client_title_more_from_season, it) }
                ?: stringResource(R.string.client_title_more_episodes),
        episodes = others.map { it.row(imageUrl, onToggle = null) },
        shown = ALL_EPISODES,
        onOpenEpisode = { key -> others.firstOrNull { it.id == key }?.let(actions.onOpenEpisode) },
        stateContent =
            when (season) {
                EpisodesState.Loading -> ({ repeat(SKELETON_ROWS) { EpisodeListRowSkeleton() } })
                is EpisodesState.Failed -> ({ SeasonFailed(season, actions.onRetrySeason) })
                is EpisodesState.Ready -> null
            },
    )
}

@Composable
private fun SeasonFailed(
    failed: EpisodesState.Failed,
    onRetry: () -> Unit,
) {
    Text(stringResource(R.string.client_series_episodes_failed))
    if (failed.failure.canRetry()) TextButton(onClick = onRetry) { Text(stringResource(DesR.string.error_kind_retry)) }
}

/** Who is on the page, guest cast first. */
private fun EpisodePageUi.people(): List<PersonUi> = guestCast + crew

/** The round buttons beside the primary: watched and favourite, which show their state, the series the episode belongs to, and for an episode with several files, the file to play. */
@Composable
private fun episodeRoundActions(
    episode: EpisodePageUi,
    actions: EpisodeActions,
    onChooseVersion: () -> Unit,
): List<DetailRoundAction> {
    val markWatched = stringResource(if (episode.watched) R.string.client_title_mark_unwatched else R.string.client_title_mark_watched)
    val markFavourite =
        stringResource(if (episode.favourite) R.string.client_title_remove_favourite else R.string.client_title_add_favourite)
    return buildList {
        add(
            DetailRoundAction(
                icon = if (episode.watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                label = stringResource(R.string.client_title_watched),
                onClick = actions.onToggleWatched,
                contentDescription = markWatched,
            ),
        )
        add(
            DetailRoundAction(
                icon = if (episode.favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(R.string.client_title_favourite),
                onClick = actions.onToggleFavourite,
                contentDescription = markFavourite,
            ),
        )
        episode.seriesId?.let { seriesId ->
            add(
                DetailRoundAction(
                    icon = Icons.Filled.Tv,
                    label = stringResource(R.string.client_title_series),
                    onClick = { actions.onOpenSeries(seriesId) },
                    contentDescription = stringResource(R.string.client_title_go_to_series),
                ),
            )
        }
        if (episode.versions.size >
            1
        ) {
            add(DetailRoundAction(Icons.Filled.VideoLibrary, stringResource(R.string.client_title_versions), onChooseVersion))
        }
    }
}

@Composable
private fun PlayAction.label(): String =
    when {
        kind == PlayKind.Play -> stringResource(R.string.client_title_play)
        minutesLeft != null -> stringResource(R.string.client_title_resume_left, minutesLeft)
        else -> stringResource(R.string.client_title_resume)
    }

/** The stats the server gave, each a cell; a stat it did not give is left out. */
@Composable
private fun EpisodeStats.cells(locale: Locale): List<DetailStat> =
    listOfNotNull(
        rating?.let { DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(DesR.string.detail_rating)) },
        runtimeMinutes?.let {
            DetailStat(
                Icons.Filled.Schedule,
                stringResource(DesR.string.detail_runtime_minutes, it),
                stringResource(R.string.client_title_runtime),
            )
        },
        certification?.let { DetailStat(Icons.Filled.Shield, it, stringResource(R.string.client_title_certification)) },
    )
