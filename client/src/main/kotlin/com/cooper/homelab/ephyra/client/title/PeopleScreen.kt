package com.cooper.homelab.ephyra.client.title

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.PeopleGridPerson
import com.binge.designsystem.template.PeopleGridScreen
import com.binge.designsystem.template.PeopleGridSection
import com.binge.designsystem.template.PeopleGridState
import com.binge.designsystem.template.PeopleGridTab
import com.cooper.homelab.ephyra.client.R
import com.cooper.homelab.ephyra.client.home.canRetry
import com.cooper.homelab.ephyra.client.home.errorKind
import com.binge.designsystem.R as DesR

/** What the cast and crew page does: [onBack] leaves it, [onRetry] reads the title again, and [onOpenPerson] opens a person's page. */
class TitlePeopleActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onOpenPerson: (PersonUi) -> Unit,
)

/** The tab the page opens on when the film's or series' page sent the user from its Cast rail. */
const val CAST_TAB = 0

/** The tab the page opens on when the user came from the Crew rail. */
const val CREW_TAB = 1

/**
 * A title's cast and crew, on the design system's people grid: a Cast tab and a Crew tab over a pager, swiped or tapped between. The crew is
 * in sections by what each did. [initialTab] is the tab to open on, and the user's own choice after that is kept across a rotation.
 * [imageUrl] turns a photo into a URL at a width in pixels, so this screen holds no server and no token.
 */
@Composable
fun TitlePeopleScreen(
    state: TitlePeopleUiState,
    initialTab: Int,
    imageUrl: (TitleImage, Int) -> String?,
    actions: TitlePeopleActions,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(CAST_TAB, CREW_TAB)) }
    val photoWidth = with(LocalDensity.current) { dimensionResource(DesR.dimen.person_card_photo_size).roundToPx() }
    val jobs = crewJobWords()
    val ready = (state as? TitlePeopleUiState.Ready)?.people
    PeopleGridScreen(
        title = ready?.title ?: stringResource(R.string.client_people_title),
        tabs =
            listOf(
                PeopleGridTab(
                    stringResource(R.string.client_title_cast),
                    tabState(state, stringResource(R.string.client_people_cast_empty)) { people ->
                        listOf(PeopleGridSection("cast", null, people.cast.map { it.gridPerson(null, imageUrl, photoWidth) }))
                    },
                ),
                PeopleGridTab(
                    stringResource(R.string.client_title_crew),
                    tabState(state, stringResource(R.string.client_people_crew_empty)) { people ->
                        people.crew.groupBy { it.job }.map { (job, group) ->
                            PeopleGridSection(
                                job?.name ?: "other",
                                job?.let(jobs::get),
                                group.map { it.gridPerson(jobs[job], imageUrl, photoWidth) },
                            )
                        }
                    },
                    sectionBubble = true,
                ),
            ),
        selectedTab = selected,
        onTabChange = { selected = it },
        onOpenPerson = { key ->
            ready?.let { (it.cast + it.crew).firstOrNull { person -> person.key() == key } }?.let(actions.onOpenPerson)
        },
        onRetry = { actions.onRetry() },
        modifier = modifier,
        onBack = actions.onBack,
        loadingLabel = stringResource(R.string.client_people_loading),
    )
}

/** One tab's state from the page's: a skeleton while it loads, the failure's own words, a plate when nobody is listed, else its sections. */
private fun tabState(
    state: TitlePeopleUiState,
    empty: String,
    sections: (TitlePeopleUi) -> List<PeopleGridSection>,
): PeopleGridState =
    when (state) {
        TitlePeopleUiState.Loading -> PeopleGridState.Loading
        is TitlePeopleUiState.Failed -> PeopleGridState.Failed(state.failure.errorKind(), retryable = state.failure.canRetry())
        is TitlePeopleUiState.Ready ->
            sections(state.people)
                .filter {
                    it.people.isNotEmpty()
                }.let { if (it.isEmpty()) PeopleGridState.Empty(empty) else PeopleGridState.Ready(it) }
    }

private fun PersonUi.gridPerson(
    jobWord: String?,
    imageUrl: (TitleImage, Int) -> String?,
    photoWidth: Int,
) = PeopleGridPerson(key(), name, subtitle ?: jobWord, photo?.let { imageUrl(it, photoWidth) })
