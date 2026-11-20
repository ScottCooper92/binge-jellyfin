package com.cooper.homelab.ephyra.client.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.PersonFact
import com.binge.designsystem.template.CreditSection
import com.binge.designsystem.template.GridItem
import com.binge.designsystem.template.PersonDetail
import com.binge.designsystem.template.PersonDetailCallbacks
import com.binge.designsystem.template.PersonDetailScreen
import com.binge.designsystem.template.PersonDetailState
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import java.util.Locale
import com.binge.designsystem.template.CreditsState as DesCreditsState

/** What the person page does when the user acts. A credit is handed back as the title's id and kind. */
class PersonActions(
    val onBack: (() -> Unit)?,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onOpenTitle: (TitleCard) -> Unit,
)

private const val PHOTO_WIDTH = 400
private const val POSTER_WIDTH = 300

/** The person page over the design system's template: mapping and callbacks only. */
@Composable
fun PersonScreen(
    state: PersonUiState,
    imageUrl: (TitleImage, Int) -> String?,
    actions: PersonActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val facts = (state as? PersonUiState.Ready)?.person?.let { personFacts(it.facts, locale) }.orEmpty()
    val titlesHeading = stringResource(R.string.client_person_titles)
    val emptyMessage = stringResource(R.string.client_person_empty)
    val mapped =
        when (state) {
            PersonUiState.Loading -> PersonDetailState.Loading()
            is PersonUiState.Failed -> PersonDetailState.Failed(kind = state.failure.errorKind(), retryable = state.failure.canRetry())
            is PersonUiState.Ready -> PersonDetailState.Content(personDetail(state.person, facts, titlesHeading, emptyMessage, imageUrl))
        }
    val byKey =
        remember(state) {
            (state as? PersonUiState.Ready)
                ?.person
                ?.credits
                .orEmpty()
                .associateBy { it.key() }
        }
    PersonDetailScreen(
        state = mapped,
        callbacks =
            PersonDetailCallbacks(
                onBack = actions.onBack,
                onRetry = actions.onRetry,
                onOpenCredit = { key -> byKey[key]?.let(actions.onOpenTitle) },
                onLoadMoreCredits = { actions.onLoadMore() },
                onRetryCredits = actions.onLoadMore,
            ),
    )
}

/** The profile's labelled lines; a fact the server did not give is left out. */
@Composable
private fun personFacts(
    facts: PersonFacts,
    locale: Locale,
): List<PersonFact> =
    listOfNotNull(
        facts.born?.let { PersonFact(premiere(it, locale), stringResource(R.string.client_person_born)) },
        facts.died?.let { PersonFact(premiere(it, locale), stringResource(R.string.client_person_died)) },
        facts.birthPlace?.let { PersonFact(it, stringResource(R.string.client_person_birthplace)) },
    )

internal fun personDetail(
    person: PersonPageUi,
    facts: List<PersonFact>,
    titlesHeading: String,
    emptyMessage: String,
    imageUrl: (TitleImage, Int) -> String?,
): PersonDetail =
    PersonDetail(
        name = person.name,
        photoUrl = person.photo?.let { imageUrl(it, PHOTO_WIDTH) },
        facts = facts,
        biography = person.biography,
        credits = credits(person, titlesHeading, emptyMessage, imageUrl),
    )

/**
 * One section, "Titles": Jellyfin pages one list of a person's movies and series, so the page is one rail. Titles already read stay when a
 * later page fails, and the failure becomes the rail's own Try again tile.
 */
internal fun credits(
    person: PersonPageUi,
    heading: String,
    emptyMessage: String,
    imageUrl: (TitleImage, Int) -> String?,
): DesCreditsState {
    val items =
        person.credits.map { card ->
            GridItem(
                key = card.key(),
                posterUrl = card.poster?.let { imageUrl(it, POSTER_WIDTH) },
                title = card.title,
                rating = card.rating?.toFloat(),
                subtitle = card.year?.toString(),
                watched = card.played,
            )
        }
    val state = person.creditsState
    return when {
        items.isEmpty() && state is CreditsState.Loading -> DesCreditsState.Loading
        items.isEmpty() && state is CreditsState.Ready && state.hasMore -> DesCreditsState.Loading
        items.isEmpty() && state is CreditsState.Failed -> DesCreditsState.Failed(retryable = state.failure.canRetry())
        items.isEmpty() && state is CreditsState.Ready && !state.hasMore -> DesCreditsState.Empty(emptyMessage)
        else ->
            DesCreditsState.Ready(
                listOf(
                    CreditSection(
                        key = "titles",
                        title = heading,
                        items = items,
                        hasMore = (state as? CreditsState.Ready)?.hasMore == true,
                        appending = state is CreditsState.Loading,
                        appendFailed = state is CreditsState.Failed,
                    ),
                ),
            )
    }
}
