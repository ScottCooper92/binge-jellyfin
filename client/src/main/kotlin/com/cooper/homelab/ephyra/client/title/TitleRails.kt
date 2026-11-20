package com.cooper.homelab.ephyra.client.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.DetailPerson
import com.binge.designsystem.component.DetailPoster
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

/** The facts at the end of the page: the original title, the studios and the premiere date. Each is left out when the server gave none. */
@Composable
internal fun TitleFacts.entries(locale: Locale): List<DetailInfoEntry> =
    listOf(
        DetailInfoEntry(Icons.Filled.Translate, stringResource(R.string.client_title_original_title), originalTitle),
        DetailInfoEntry(Icons.Filled.Business, stringResource(R.string.client_title_studios), studios.joinToString().ifBlank { null }),
        DetailInfoEntry(Icons.Filled.Event, stringResource(R.string.client_title_premiere), premiereDate?.let { premiere(it, locale) }),
    )
