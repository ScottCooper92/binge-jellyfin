package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.client.title.CrewJob
import com.cooper.homelab.ephyra.client.title.PersonUi
import com.cooper.homelab.ephyra.client.title.TitleImage
import com.cooper.homelab.ephyra.client.title.TitlePeopleActions
import com.cooper.homelab.ephyra.client.title.TitlePeopleScreen
import com.cooper.homelab.ephyra.client.title.TitlePeopleUi
import com.cooper.homelab.ephyra.client.title.TitlePeopleUiState
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/**
 * A title's cast and crew page (`:client`) on the Cast tab and on the Crew tab, in English and Spanish and at larger fonts, loading, failed,
 * and with nobody listed in a tab. No picture is loaded, so each frame shows the initials and is the same every run.
 */
class ClientTitlePeopleScreenshotTest {
    private fun actor(
        id: String,
        name: String,
        role: String,
    ) = PersonUi(id, name, role, TitleImage(id, ImageKind.Primary, "p-$id"))

    private fun crew(
        id: String,
        name: String,
        job: CrewJob,
    ) = PersonUi(id, name, null, TitleImage(id, ImageKind.Primary, "p-$id"), job)

    private val people =
        TitlePeopleUi(
            title = "The Long Quiet",
            cast =
                listOf(
                    actor("a1", "Amara Okafor", "Wren"),
                    actor("a2", "Jonas Weil", "The Harbourmaster"),
                    actor("a3", "Priya Nair", "Dr Sethi"),
                    actor("a4", "Tomas Reyes", "Keeper Ardal"),
                    actor("a5", "Lena Holm", "Ingrid"),
                    actor("a6", "Mateo Rossi", "The Courier"),
                ),
            crew =
                listOf(
                    crew("c1", "Mira Lund", CrewJob.Director),
                    crew("c2", "Ines Calder", CrewJob.Writer),
                    crew("c3", "Tomas Rey", CrewJob.Writer),
                    crew("c4", "Noor Haddad", CrewJob.Producer),
                    crew("c5", "Ola Berg", CrewJob.Composer),
                ),
        )

    @Composable
    private fun People(
        state: TitlePeopleUiState,
        tab: Int = 0,
    ) = TitlePeopleScreen(
        state = state,
        initialTab = tab,
        imageUrl = { _, _ -> null },
        actions = TitlePeopleActions({}, {}, {}),
    )

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun cast() = People(TitlePeopleUiState.Ready(people))

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun crew() = People(TitlePeopleUiState.Ready(people), tab = 1)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noCrew() = People(TitlePeopleUiState.Ready(people.copy(crew = emptyList())), tab = 1)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun noCast() = People(TitlePeopleUiState.Ready(people.copy(cast = emptyList())))

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = People(TitlePeopleUiState.Loading)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = People(TitlePeopleUiState.Failed(ServerFailure.Unreachable()))
}
