package com.cooper.homelab.ephyra.client.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailPerson
import com.binge.designsystem.component.DetailPoster
import com.binge.designsystem.component.EpisodeRow
import com.binge.designsystem.component.ExternalScore
import com.binge.designsystem.component.ExternalScoreKind
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.template.PersonItem
import com.binge.designsystem.template.PosterItem
import com.binge.designsystem.theme.BingeSentiment
import com.cooper.homelab.ephyra.client.R
import java.util.Locale
import com.binge.designsystem.R as DesR

/*
 * What every title page draws the same way: the people rails, the poster rails, and the facts at the end. The movie, series, season and
 * episode pages hold their own sections and take these, so a rail reads one way across the app.
 */

/** A person's key on a rail: text, as a list's key must be saveable. One person is two items when they have two roles, so the role is in it. */
internal fun PersonUi.key(): String = "$id|$job|$subtitle"

/** A poster card's key on a rail: its item id. */
internal fun TitleCard.key(): String = id

/** The people of a rail as the template's items, each keyed so a tap finds its person. [imageUrl] turns a photo into a URL at a width. */
@Composable
internal fun peopleItems(
    people: List<PersonUi>,
    imageUrl: (TitleImage, Int) -> String?,
): List<PersonItem> {
    val photoWidth = with(LocalDensity.current) { dimensionResource(DesR.dimen.person_card_photo_size).roundToPx() }
    val jobs = crewJobWords()
    return people.map { person ->
        PersonItem(
            person.key(),
            DetailPerson(person.name, person.subtitle ?: person.job?.let(jobs::get), person.photo?.let { imageUrl(it, photoWidth) }),
        )
    }
}

/** The titles of a rail as the template's poster items, each keyed by its id, with a watched mark on one the user has finished. */
@Composable
internal fun posterItems(
    cards: List<TitleCard>,
    imageUrl: (TitleImage, Int) -> String?,
): List<PosterItem> {
    val cardWidth = with(LocalDensity.current) { dimensionResource(DesR.dimen.card_width).roundToPx() }
    val watched = stringResource(R.string.client_title_watched)
    return cards.map { PosterItem(it.key(), it.poster(imageUrl, cardWidth, watched)) }
}

/** What each crew job is called, in the user's language. The server sends no text for it. */
@Composable
private fun crewJobWords(): Map<CrewJob, String> =
    mapOf(
        CrewJob.Director to stringResource(R.string.client_title_job_director),
        CrewJob.Writer to stringResource(R.string.client_title_job_writer),
        CrewJob.Producer to stringResource(R.string.client_title_job_producer),
        CrewJob.Composer to stringResource(R.string.client_title_job_composer),
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

/** The critics' score at or above this reads as fresh (a tomato), below it as rotten (a splat), as Rotten Tomatoes draws it. */
private const val FRESH_THRESHOLD_PERCENT = 60

private const val MINUTES_PER_HOUR = 60

/** The critics' score as the header's score line, or nothing when the server holds none. */
@Composable
internal fun criticsScores(percent: Int?): List<ExternalScore> =
    listOfNotNull(
        percent?.let {
            ExternalScore(
                if (it >= FRESH_THRESHOLD_PERCENT) ExternalScoreKind.CriticsFresh else ExternalScoreKind.CriticsRotten,
                "$it%",
                stringResource(R.string.client_title_score_critics, it),
            )
        },
    )

/** A runtime as hours and minutes, or as minutes alone under an hour. */
@Composable
internal fun runtimeText(minutes: Int): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(DesR.string.detail_runtime_minutes, minutes)
    } else {
        stringResource(R.string.client_title_runtime_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }

/** A series' status in the user's language when it is one the server names, else the server's own text. */
@Composable
private fun statusText(status: String): String =
    when {
        status.equals("Continuing", ignoreCase = true) -> stringResource(R.string.client_series_status_continuing)
        status.equals("Ended", ignoreCase = true) -> stringResource(R.string.client_series_status_ended)
        else -> status
    }

/** A film's details: who directed it, when it came out, the studios and the original title. Each is left out when the server gave none. */
@Composable
internal fun TitleFacts.movieEntries(locale: Locale): List<DetailInfoEntry> =
    listOf(
        DetailInfoEntry(
            Icons.Filled.Videocam,
            stringResource(R.string.client_title_directed_by),
            directors.joinToString().ifBlank { null },
        ),
        DetailInfoEntry(Icons.Filled.Event, stringResource(R.string.client_title_release_date), premiereDate?.let { premiere(it, locale) }),
        DetailInfoEntry(Icons.Filled.Business, stringResource(R.string.client_title_studios), studios.joinToString().ifBlank { null }),
        DetailInfoEntry(Icons.Filled.Translate, stringResource(R.string.client_title_original_title), originalTitle),
    )

/** A series' details: who made it, its status, when it first and last aired, the studios and the original title. */
@Composable
internal fun TitleFacts.seriesEntries(locale: Locale): List<DetailInfoEntry> =
    listOf(
        DetailInfoEntry(Icons.Filled.Person, stringResource(R.string.client_title_created_by), creators.joinToString().ifBlank { null }),
        DetailInfoEntry(Icons.Filled.Tv, stringResource(R.string.client_series_status), status?.let { statusText(it) }),
        DetailInfoEntry(Icons.Filled.Event, stringResource(R.string.client_title_first_aired), premiereDate?.let { premiere(it, locale) }),
        DetailInfoEntry(
            Icons.Filled.EventAvailable,
            stringResource(R.string.client_title_last_aired),
            endDate?.let { premiere(it, locale) },
        ),
        DetailInfoEntry(Icons.Filled.Business, stringResource(R.string.client_title_studios), studios.joinToString().ifBlank { null }),
        DetailInfoEntry(Icons.Filled.Translate, stringResource(R.string.client_title_original_title), originalTitle),
    )

/** The width of an episode's still in its row. */
private const val STILL_WIDTH = 480

/**
 * An episode as the template's row: its number or none, name, still, runtime in the supporting line, and a progress bar. With [onToggle] the
 * row ends in a watched toggle; with none a watched episode shows the template's check.
 */
@Composable
internal fun EpisodeUi.row(
    imageUrl: (TitleImage, Int) -> String?,
    onToggle: ((EpisodeUi) -> Unit)?,
): EpisodeRow {
    val markWatched = stringResource(if (watched) R.string.client_title_mark_unwatched else R.string.client_title_mark_watched)
    val runtime = runtimeMinutes?.let { stringResource(DesR.string.detail_runtime_minutes, it) }
    return EpisodeRow(
        number = number,
        name = title,
        stillUrl = still?.let { imageUrl(it, STILL_WIDTH) },
        progress = progress,
        key = id,
        supporting = runtime,
        // With a toggle the row ends in it; without one the template's own check marks a watched episode.
        played = watched,
        playedLabel = stringResource(R.string.client_title_watched),
        trailing =
            onToggle?.let { toggle ->
                {
                    IconButton(onClick = { toggle(this) }) {
                        Icon(
                            imageVector = if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                            contentDescription = markWatched,
                        )
                    }
                }
            },
    )
}
