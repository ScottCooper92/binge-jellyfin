package com.cooper.homelab.ephyra.client.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.binge.companion.sdk.attempt
import com.binge.designsystem.component.BingeFilledButton
import com.binge.designsystem.component.DetailActions
import com.binge.designsystem.component.DetailActionsSkeleton
import com.binge.designsystem.component.DetailHeroSkeleton
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailInfoSection
import com.binge.designsystem.component.DetailOverviewSection
import com.binge.designsystem.component.DetailOverviewSkeleton
import com.binge.designsystem.component.DetailPeopleRail
import com.binge.designsystem.component.DetailPerson
import com.binge.designsystem.component.DetailPoster
import com.binge.designsystem.component.DetailPosterRail
import com.binge.designsystem.component.DetailRoundAction
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatsRows
import com.binge.designsystem.component.DetailStatsRowsSkeleton
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.binge.designsystem.template.ErrorScreen
import com.binge.designsystem.template.HeroDetailScreen
import com.binge.designsystem.template.HeroDetailStateScreen
import com.binge.designsystem.template.MediaHeroDetailPage
import com.binge.designsystem.template.MediaWideHeader
import com.binge.designsystem.template.readingMargin
import com.binge.designsystem.theme.BingeSentiment
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

/** How many round actions the loading row stands for: the watched mark and the favourite, the two every film has. */
private const val SKELETON_ACTIONS = 2

/** The length of an ISO date (`yyyy-MM-dd`) at the front of the server's premiere date. */
private const val ISO_DATE_LENGTH = 10

/**
 * What the movie page does: [onBack] leaves it, [onRetry] reads the title again after a failure, and [onPlay] is the primary button,
 * which opens nothing yet (in-app play is #756). [onOpenTitle] and [onOpenPerson] open a poster card's or a person's page.
 */
class MovieActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onPlay: () -> Unit,
    val onToggleWatched: () -> Unit,
    val onToggleFavourite: () -> Unit,
    val onOpenTitle: (TitleCard) -> Unit,
    val onOpenPerson: (PersonUi) -> Unit,
)

/**
 * The movie page for [state]. [imageUrl] turns a picture into a URL at a width in pixels, or null when there is none, so this screen
 * holds no server and no token. A write that fails says so in a snackbar, from [events]. A loading page is the page's own skeleton under the same back control, and a failure is the design
 * system's error screen under it, so back works whether or not the load does.
 */
@Composable
fun MovieScreen(
    state: MovieUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: MovieActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    events: Flow<MovieEvent> = emptyFlow(),
) {
    val writeFailed = stringResource(R.string.client_movie_write_failed)
    LaunchedEffect(events) { events.collect { snackbarHostState.showSnackbar(writeFailed) } }
    when (state) {
        MovieUiState.Loading -> MovieSkeleton(actions.onBack, snackbarHostState, modifier)
        is MovieUiState.Failed ->
            HeroDetailStateScreen(actions.onBack, modifier, snackbarHostState) {
                ErrorScreen(state.failure.errorKind(), actions.onRetry.takeIf { state.failure.canRetry() }, Modifier.fillMaxSize())
            }
        is MovieUiState.Ready -> MovieContent(state.movie, imageUrl, actions, snackbarHostState, modifier)
    }
}

/** The page before the title has answered: the hero, the stats, the actions and the overview as plates at the height the loaded page gives them. */
@Composable
fun MovieSkeleton(
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val cinematic = isExpandedLayout()
    HeroDetailScreen(
        title = "",
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        heroHeight = dimensionResource(if (cinematic) DesR.dimen.detail_cinematic_header_height else DesR.dimen.detail_hero_height),
        horizontalInset = dimensionResource(if (cinematic) DesR.dimen.detail_cinematic_header_padding else DesR.dimen.padding_m),
        contentMaxWidth = Dp.Infinity,
        darkStatusBar = false,
        hero = { DetailHeroSkeleton(cinematic) },
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
            if (!cinematic) {
                DetailStatsRowsSkeleton(Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.STATS)), count = SKELETON_STATS)
            }
            DetailActionsSkeleton(Modifier.readingMargin(), buttons = SKELETON_ACTIONS)
            if (!cinematic) DetailOverviewSkeleton(Modifier.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.OVERVIEW)))
        }
    }
}

/** How many stats the loading page stands for: a film's usual four (rating, year, runtime, certification). */
private const val SKELETON_STATS = 4

@Composable
private fun MovieContent(
    movie: MovieUi,
    imageUrl: (TitleImage, Int) -> String?,
    actions: MovieActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    var version by rememberSaveable { mutableStateOf<String?>(null) }
    val cinematic = isExpandedLayout()
    val stats = movie.stats.cells()
    val synopsis = movie.overview
    MediaHeroDetailPage(
        title = movie.title,
        backdropUrl = movie.backdrop?.let { imageUrl(it, HERO_PICTURE_WIDTH) },
        metaText = "",
        onBack = actions.onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        genres = movie.genres,
        tagline = movie.tagline,
        wideHeader = MediaWideHeader(movie.poster?.let { imageUrl(it, POSTER_WIDTH) }, synopsis, stats),
    ) {
        Spacer(Modifier.height(dimensionResource(DesR.dimen.padding_m)))
        Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
            // At the expanded width the cinematic header carries the stats and the synopsis, so the body keeps only the rest.
            if (!cinematic && stats.isNotEmpty()) DetailStatsRows(stats)
            MovieActionRow(movie, actions, onChooseVersion = { choosing = true })
            if (!cinematic) DetailOverviewSection(stringResource(R.string.client_movie_overview), synopsis)
            MovieRails(movie, imageUrl, actions)
            MovieFacts(movie.facts)
        }
    }
    if (choosing) {
        VersionSheet(
            versions = movie.versions,
            selected = version ?: movie.versions.firstOrNull()?.id,
            onSelect = {
                version = it
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

/** The primary button and the round actions under it: watched, favourite and, for a film with several files, the file to play. */
@Composable
private fun MovieActionRow(
    movie: MovieUi,
    actions: MovieActions,
    onChooseVersion: () -> Unit,
) {
    val watchedLabel = stringResource(R.string.client_movie_watched)
    val favouriteLabel = stringResource(R.string.client_movie_favourite)
    val round =
        buildList {
            add(
                DetailRoundAction(
                    icon = if (movie.watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    label = watchedLabel,
                    onClick = actions.onToggleWatched,
                    contentDescription =
                        stringResource(if (movie.watched) R.string.client_movie_mark_unwatched else R.string.client_movie_mark_watched),
                ),
            )
            add(
                DetailRoundAction(
                    icon = if (movie.favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = favouriteLabel,
                    onClick = actions.onToggleFavourite,
                    contentDescription =
                        stringResource(
                            if (movie.favourite) R.string.client_movie_remove_favourite else R.string.client_movie_add_favourite,
                        ),
                ),
            )
            if (movie.versions.size > 1) {
                add(DetailRoundAction(Icons.Filled.VideoLibrary, stringResource(R.string.client_movie_versions), onChooseVersion))
            }
        }
    DetailActions(
        primary = { sized ->
            BingeFilledButton(
                label = movie.play.label(),
                onClick = actions.onPlay,
                modifier = sized,
                leadingIcon = Icons.Filled.PlayArrow,
            )
        },
        roundActions = round,
        modifier = Modifier.readingMargin(),
    )
}

@Composable
private fun PlayAction.label(): String =
    when {
        kind == PlayKind.Play -> stringResource(R.string.client_movie_play)
        minutesLeft != null -> stringResource(R.string.client_movie_resume_left, minutesLeft)
        else -> stringResource(R.string.client_movie_resume)
    }

/** The cast, the crew, the collection the film is in and similar films, each a row that is left out when it has nothing. */
@Composable
private fun MovieRails(
    movie: MovieUi,
    imageUrl: (TitleImage, Int) -> String?,
    actions: MovieActions,
) {
    val watched = stringResource(R.string.client_movie_watched)
    val cardWidth = with(LocalDensity.current) { dimensionResource(DesR.dimen.card_width).roundToPx() }
    val photoWidth = with(LocalDensity.current) { dimensionResource(DesR.dimen.person_card_photo_size).roundToPx() }
    DetailPeopleRail(
        title = stringResource(R.string.client_movie_cast),
        people = movie.cast,
        itemKey = { "cast-${it.id}-${it.subtitle}" },
        person = { DetailPerson(it.name, it.subtitle, it.photo?.let { photo -> imageUrl(photo, photoWidth) }) },
        onOpen = actions.onOpenPerson,
    )
    val jobs = crewJobWords()
    DetailPeopleRail(
        title = stringResource(R.string.client_movie_crew),
        people = movie.crew,
        itemKey = { "crew-${it.id}-${it.job}-${it.subtitle}" },
        person = { DetailPerson(it.name, it.subtitle ?: it.job?.let(jobs::get), it.photo?.let { photo -> imageUrl(photo, photoWidth) }) },
        onOpen = actions.onOpenPerson,
    )
    movie.collection?.let { box ->
        DetailPosterRail(
            title = box.name,
            items = box.parts,
            itemKey = { it.id },
            poster = { it.poster(imageUrl, cardWidth, watched) },
            onOpen = actions.onOpenTitle,
        )
    }
    DetailPosterRail(
        title = stringResource(R.string.client_movie_similar),
        items = movie.similar,
        itemKey = { it.id },
        poster = { it.poster(imageUrl, cardWidth, watched) },
        onOpen = actions.onOpenTitle,
    )
}

/** What each crew job is called, in the user's language. The server sends no text for it. */
@Composable
private fun crewJobWords(): Map<CrewJob, String> =
    mapOf(
        CrewJob.Director to stringResource(R.string.client_movie_job_director),
        CrewJob.Writer to stringResource(R.string.client_movie_job_writer),
        CrewJob.Producer to stringResource(R.string.client_movie_job_producer),
        CrewJob.Composer to stringResource(R.string.client_movie_job_composer),
    )

private fun TitleCard.poster(
    imageUrl: (TitleImage, Int) -> String?,
    width: Int,
    watched: String,
): DetailPoster =
    DetailPoster(
        posterUrl = poster?.let { imageUrl(it, width) },
        title = title,
        rating = rating?.toFloat(),
        status = if (played) MediaCardStatus(watched, BingeSentiment.Positive) else null,
        subtitle = year?.toString(),
    )

/** The facts at the end of the page: the original title, the studios and the premiere date. Each is left out when the server gave none. */
@Composable
private fun MovieFacts(facts: MovieFacts) {
    val locale = LocalConfiguration.current.locales[0]
    DetailInfoSection(
        heading = stringResource(R.string.client_movie_details),
        entries =
            listOf(
                DetailInfoEntry(Icons.Filled.Translate, stringResource(R.string.client_movie_original_title), facts.originalTitle),
                DetailInfoEntry(
                    Icons.Filled.Business,
                    stringResource(R.string.client_movie_studios),
                    facts.studios.joinToString().ifBlank { null },
                ),
                DetailInfoEntry(
                    Icons.Filled.Event,
                    stringResource(R.string.client_movie_premiere),
                    facts.premiereDate?.let { premiere(it, locale) },
                ),
            ),
    )
}

/** The premiere date in the user's locale, or the server's text when it is not a date. */
internal fun premiere(
    text: String,
    locale: Locale,
): String =
    attempt { LocalDate.parse(text.take(ISO_DATE_LENGTH)) }
        .map { it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
        .getOrDefault(text)

/** The stats the server gave, each a cell; a stat it did not give is left out. */
@Composable
private fun MovieStats.cells(): List<DetailStat> {
    // The configuration's locale is observable, so a change of language recomposes the stats.
    val locale = LocalConfiguration.current.locales[0]
    return listOfNotNull(
        rating?.let { DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), stringResource(DesR.string.detail_rating)) },
        year?.let { DetailStat(Icons.Filled.CalendarMonth, it.toString(), stringResource(R.string.client_movie_year)) },
        runtimeMinutes?.let {
            DetailStat(
                Icons.Filled.Schedule,
                stringResource(DesR.string.detail_runtime_minutes, it),
                stringResource(R.string.client_movie_runtime),
            )
        },
        certification?.let { DetailStat(Icons.Filled.Shield, it, stringResource(R.string.client_movie_certification)) },
    )
}
