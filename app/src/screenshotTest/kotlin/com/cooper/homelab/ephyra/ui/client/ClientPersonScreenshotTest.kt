package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.title.CreditsState
import com.cooper.homelab.ephyra.client.title.PersonActions
import com.cooper.homelab.ephyra.client.title.PersonFacts
import com.cooper.homelab.ephyra.client.title.PersonPageUi
import com.cooper.homelab.ephyra.client.title.PersonScreen
import com.cooper.homelab.ephyra.client.title.PersonUiState
import com.cooper.homelab.ephyra.client.title.TitleCard
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * The person page (`:client`) in the states the issue names: loaded with their titles, a next page being read, a next page that failed,
 * the first page loading and failed, no titles, a sparse person, and the whole page loading and failed. No picture is loaded, so each frame
 * shows the placeholders and the initials, and is the same every run.
 */
class ClientPersonScreenshotTest {
    private fun card(
        id: String,
        title: String,
        year: Int,
        kind: ItemKind = ItemKind.Movie,
        played: Boolean = false,
    ) = TitleCard(id, kind, title, year, 7.6, TitleImage(id, ImageKind.Primary, "p-$id"), played)

    private val credits =
        listOf(
            card("f1", "The Long Quiet", 2021),
            card("f2", "Night Market", 2019, played = true),
            card("f3", "Paper Boats", 2023),
            card("f4", "Salt Road", 2017, ItemKind.Series),
            card("f5", "Low Tide", 2015),
        )

    private val person =
        PersonPageUi(
            id = "p1",
            name = "Amara Okafor",
            photo = TitleImage("p1", ImageKind.Primary, "pt1"),
            facts = PersonFacts(born = "1984-03-12T00:00:00Z", died = null, birthPlace = "Lagos, Nigeria"),
            biography =
                "Amara Okafor trained in theatre before her first film role in 2009. She is known for quiet, watchful performances, " +
                    "and for a long working partnership with the director Tomas Reyes. She lives between Lagos and Lisbon.",
            credits = credits,
            creditsState = CreditsState.Ready(hasMore = false),
        )

    @Composable
    private fun Person(state: PersonUiState) =
        PersonScreen(state = state, imageUrl = { _, _ -> null }, actions = PersonActions({}, {}, {}, {}))

    private fun ready(page: PersonPageUi) = PersonUiState.Ready(page)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun loaded() = Person(ready(person))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun nextPageLoading() = Person(ready(person.copy(credits = credits.take(2), creditsState = CreditsState.Loading)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun nextPageFailed() =
        Person(ready(person.copy(credits = credits.take(2), creditsState = CreditsState.Failed(ServerFailure.Unreachable()))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun creditsLoading() = Person(ready(person.copy(credits = emptyList(), creditsState = CreditsState.Loading)))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun creditsFailed() = Person(ready(person.copy(credits = emptyList(), creditsState = CreditsState.Failed(ServerFailure.Unreachable()))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noCredits() = Person(ready(person.copy(credits = emptyList(), creditsState = CreditsState.Ready(hasMore = false))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sparse() =
        Person(
            ready(
                person.copy(
                    photo = null,
                    facts = PersonFacts(null, null, null),
                    biography = null,
                    credits = emptyList(),
                ),
            ),
        )

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun diedWithoutBirthplace() = Person(ready(person.copy(facts = PersonFacts("1931-01-02", "2009-06-30", null))))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = Person(PersonUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = Person(PersonUiState.Failed(ServerFailure.Unreachable()))
}
