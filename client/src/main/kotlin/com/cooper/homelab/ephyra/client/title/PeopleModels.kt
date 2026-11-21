package com.cooper.homelab.ephyra.client.title

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.PersonKind
import com.cooper.homelab.ephyra.browse.TitleDetail

/** Everyone on a title: [cast] in the server's order, and [crew] with what each did. [title] is the film's or series' own name. */
data class TitlePeopleUi(
    val title: String,
    val cast: List<PersonUi>,
    val crew: List<PersonUi>,
)

/** What the cast and crew page shows. */
sealed interface TitlePeopleUiState {
    data object Loading : TitlePeopleUiState

    data class Ready(
        val people: TitlePeopleUi,
    ) : TitlePeopleUiState

    /** The title could not be read. [failure] is why, as the screen classifies it. */
    data class Failed(
        val failure: ServerFailure,
    ) : TitlePeopleUiState
}

private val CREW = setOf(PersonKind.Director, PersonKind.Writer, PersonKind.Producer, PersonKind.Composer)

/** The cast and crew page's model from the title the server answered: the actors, then the crew by what they did. */
fun titlePeopleUi(detail: TitleDetail): TitlePeopleUi =
    TitlePeopleUi(
        title = detail.item.name,
        cast = detail.people.filter { it.kind == PersonKind.Actor || it.kind == PersonKind.GuestStar }.map(::personUi),
        crew = detail.people.filter { it.kind in CREW }.map(::personUi),
    )
