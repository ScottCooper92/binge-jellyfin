package com.cooper.homelab.ephyra.client.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.SnackbarHostState
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
import com.binge.companion.sdk.attempt
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.template.DetailInfo
import com.binge.designsystem.template.DetailState
import com.binge.designsystem.template.MediaDetail
import com.binge.designsystem.template.MediaDetailCallbacks
import com.binge.designsystem.template.MovieDetailScreen
import com.binge.designsystem.template.PeopleRail
import com.binge.designsystem.template.PosterRail
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.binge.designsystem.R as DesR

/** The width of the hero's picture asked of the server: a phone's width, which a backdrop is drawn across. */
private const val HERO_PICTURE_WIDTH = 1080

/** The width of the poster beside the title at the expanded width. */
private const val POSTER_WIDTH = 600

/** How many stats the loading page stands for: a film's usual four (rating, year, runtime, certification). */
private const val SKELETON_STATS = 4

/** How many round actions the loading page stands for: the watched mark and the favourite, the two every film has. */
private const val SKELETON_ACTIONS = 2

/**
 * What the movie page does: [onBack] leaves it, [onRetry] reads the title again after a failure, and [onPlay] is the primary button,
 * which opens nothing yet (in-app play is #756). [onOpenTitle] and [onOpenPerson] open a poster card's or a person's page, and
 * [onOpenCollection] the page of the collection the film is in, from its rail's See all button or its trailing tile (the heading is not tappable).
 */
class MovieActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onPlay: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onToggleFavourite: () -> Unit,
    val onOpenTitle: (TitleCard) -> Unit,
    val onOpenPerson: (PersonUi) -> Unit,
    val onOpenCollection: (collectionId: String) -> Unit,
    val onOpenLink: (url: String) -> Unit,
    val onShare: (title: String, url: String) -> Unit,
)

/**
 * The movie page for [state], on the design system's movie template. This holds what is a Jellyfin film's: the mapping from the state to
 * the template's model (see [movieDetail]), the Play or Resume button as the primary action, the watched, favourite and versions buttons
 * as the round ones, and the versions sheet as an overlay. The order of the page, the wide-screen rule, the skeleton and the failure
 * frame are the template's. [imageUrl] turns a picture into a URL at a width in pixels, or null when there is none, so this screen holds
 * no server and no token. A write that fails says so in a snackbar, from [events].
 */
@Composable
fun MovieScreen(
    state: MovieUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: MovieActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    events: Flow<TitleEvent> = emptyFlow(),
) {
    val writeFailed = stringResource(R.string.client_title_write_failed)
    LaunchedEffect(events) { events.collect { snackbarHostState.showSnackbar(writeFailed) } }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var version by rememberSaveable { mutableStateOf<String?>(null) }
    val movie = (state as? MovieUiState.Ready)?.movie
    // The template hands back the key an item was given, and a key must be a type a list can save, so a key is text and the item is found by it.
    val callbacks =
        MediaDetailCallbacks(
            onBack = actions.onBack,
            onRetry = actions.onRetry,
            onOpenPerson = { key -> movie?.people()?.firstOrNull { it.key() == key }?.let(actions.onOpenPerson) },
            onOpenPoster = { key -> movie?.cards()?.firstOrNull { it.key() == key }?.let(actions.onOpenTitle) },
            // The collection's rail is the first poster rail when the film has one, so it is the only one that has a see-all.
            onSeeAllPosters = { index -> movie?.collection?.takeIf { index == 0 }?.let { actions.onOpenCollection(it.id) } },
        )
    when (state) {
        MovieUiState.Loading ->
            MovieDetailScreen(
                state = DetailState.Loading(stats = SKELETON_STATS, hasPrimary = true, roundActions = SKELETON_ACTIONS),
                callbacks = callbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        is MovieUiState.Failed -> {
            MovieDetailScreen(
                // The kind's own words say it: nothing here knows more than why the server could not be read.
                state = DetailState.Failed(retryable = state.failure.canRetry(), kind = state.failure.errorKind()),
                callbacks = callbacks,
                modifier = modifier,
                snackbarHostState = snackbarHostState,
            )
        }
        is MovieUiState.Ready -> {
            MovieDetailScreen(
                state = DetailState.Content(movieDetail(state.movie, imageUrl)),
                callbacks = callbacks,
                modifier = modifier,
                primary = { sized ->
                    BingeFilledButton(
                        label = state.movie.play.label(),
                        onClick = actions.onPlay,
                        modifier = sized,
                        leadingIcon = Icons.Filled.PlayArrow,
                    )
                },
                roundActions = movieRoundActions(state.movie, actions, onChooseVersion = { choosing = true }),
                topBarActions = shareAction(state.movie.shareUrl) { actions.onShare(state.movie.title, it) },
                afterRails = linksSection(state.movie.links, actions.onOpenLink),
                atEnd = {
                    val shown = state.movie.versions.firstOrNull { it.id == version } ?: state.movie.versions.firstOrNull()
                    shown?.let { MovieMediaInfo(it) }
                },
                overlays = {
                    if (choosing) {
                        VersionSheet(
                            versions = state.movie.versions,
                            selected =
                                version ?: state.movie.versions
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
}

/**
 * The page's model for the template: plain display values, with every word in the user's language and every picture a URL at the width
 * it is drawn. The cast and the crew are two rails whose items carry the person as their key, so one person can appear in both. The
 * collection the film is in and similar films are two poster rails, the collection's titled with its own name.
 */
@Composable
fun movieDetail(
    movie: MovieUi,
    imageUrl: (TitleImage, Int) -> String?,
): MediaDetail {
    val locale = LocalConfiguration.current.locales[0]
    return MediaDetail(
        title = movie.title,
        backdropUrl = movie.backdrop?.let { imageUrl(it, HERO_PICTURE_WIDTH) },
        posterUrl = movie.poster?.let { imageUrl(it, POSTER_WIDTH) },
        tagline = movie.tagline,
        genres = movie.genres,
        stats = movie.stats.cells(locale),
        scores = criticsScores(movie.criticsPercent),
        overview = movie.overview,
        overviewHeading = stringResource(R.string.client_title_overview),
        peopleRails =
            listOf(
                PeopleRail(stringResource(R.string.client_title_cast), peopleItems(movie.cast, imageUrl)),
                PeopleRail(stringResource(R.string.client_title_crew), peopleItems(movie.crew, imageUrl)),
            ),
        posterRails =
            listOfNotNull(
                movie.collection?.let { PosterRail(it.name, posterItems(it.parts, imageUrl), seeAll = true) },
                PosterRail(stringResource(R.string.client_title_similar), posterItems(movie.similar, imageUrl)),
            ),
        info = DetailInfo(stringResource(R.string.client_title_details), movie.facts.movieEntries(locale)),
        tags = tagsSection(movie.tags),
    )
}

/** Who is on the page, cast first. */
private fun MovieUi.people(): List<PersonUi> = cast + crew

/** Every poster on the page: the collection's parts, then similar films. */
private fun MovieUi.cards(): List<TitleCard> = collection?.parts.orEmpty() + similar

/** The round buttons beside the primary: watched and favourite, which show their state, and for a film with several files, the file to play. */
@Composable
private fun movieRoundActions(
    movie: MovieUi,
    actions: MovieActions,
    onChooseVersion: () -> Unit,
): List<DetailRoundAction> {
    val watchedLabel = stringResource(R.string.client_title_watched)
    val favouriteLabel = stringResource(R.string.client_title_favourite)
    val markWatched = stringResource(if (movie.watched) R.string.client_title_mark_unwatched else R.string.client_title_mark_watched)
    val markFavourite = stringResource(if (movie.favourite) R.string.client_title_remove_favourite else R.string.client_title_add_favourite)
    val versionsLabel = stringResource(R.string.client_title_versions)
    return buildList {
        movie.trailerUrl?.let { url -> add(trailerAction { actions.onOpenLink(url) }) }
        add(
            DetailRoundAction(
                icon = if (movie.watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                label = watchedLabel,
                onClick = actions.onToggleWatched,
                contentDescription = markWatched,
            ),
        )
        add(
            DetailRoundAction(
                icon = if (movie.favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = favouriteLabel,
                onClick = actions.onToggleFavourite,
                contentDescription = markFavourite,
            ),
        )
        if (movie.versions.size > 1) add(DetailRoundAction(Icons.Filled.VideoLibrary, versionsLabel, onChooseVersion))
    }
}

@Composable
private fun PlayAction.label(): String =
    when {
        kind == PlayKind.Play -> stringResource(R.string.client_title_play)
        minutesLeft != null -> stringResource(R.string.client_title_resume_left, minutesLeft)
        else -> stringResource(R.string.client_title_resume)
    }

/** The premiere date in the user's locale, or the server's text when it is not a date. */
internal fun premiere(
    text: String,
    locale: Locale,
): String =
    attempt { LocalDate.parse(text.take(ISO_DATE_LENGTH)) }
        .map { it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
        .getOrDefault(text)

/** The length of an ISO date (`yyyy-MM-dd`) at the front of the server's premiere date. */
private const val ISO_DATE_LENGTH = 10

/** The stats the server gave, each a cell; a stat it did not give is left out. */
@Composable
private fun MovieStats.cells(locale: Locale): List<DetailStat> =
    listOfNotNull(
        rating?.let { DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(DesR.string.detail_rating)) },
        year?.let { DetailStat(Icons.Filled.CalendarMonth, it.toString(), stringResource(R.string.client_title_released)) },
        runtimeMinutes?.let {
            DetailStat(
                Icons.Filled.Schedule,
                runtimeText(it),
                stringResource(R.string.client_title_runtime),
            )
        },
        certification?.let { DetailStat(Icons.Filled.Shield, it, stringResource(R.string.client_title_certification)) },
    )
