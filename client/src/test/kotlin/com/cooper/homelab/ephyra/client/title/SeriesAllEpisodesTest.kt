package com.cooper.homelab.ephyra.client.title

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.ShadowMeshSpecification
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The way to a season's own page is the series page's "All episodes" button, which the template draws only when the season has more
 * episodes than it lists (five). So a season of five or fewer has no route to its page, and that is meant: the series page already shows
 * every episode of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMeshSpecification::class], qualifiers = "w411dp-h4000dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeriesAllEpisodesTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun episodes(count: Int) = (1..count).map { EpisodeUi("e$it", 1, it, "Episode $it", null, null, 45, false, null) }

    private fun series(count: Int) =
        SeriesUi(
            id = "series",
            title = "The Lighthouse Keepers",
            tagline = null,
            genres = emptyList(),
            backdrop = null,
            poster = null,
            stats = SeriesStats(rating = null, year = 2020, seasonCount = 1, certification = null),
            play = null,
            watched = false,
            favourite = false,
            overview = null,
            facts = TitleFacts(null, emptyList(), null),
            cast = emptyList(),
            crew = emptyList(),
            seasons = listOf(SeasonUi("s1", 1, "Season 1", count, count, false, null)),
            selectedSeasonId = "s1",
            episodes = EpisodesState.Ready(episodes(count)),
            similar = emptyList(),
        )

    private fun show(count: Int): MutableList<String> {
        val opened = mutableListOf<String>()
        rule.setContent {
            BingeExpressiveTheme {
                SeriesScreen(
                    state = SeriesUiState.Ready(series(count)),
                    imageUrl = { _, _ -> null },
                    actions =
                        SeriesActions(
                            onBack = {},
                            onRetry = {},
                            onPlay = {},
                            onToggleWatched = {},
                            onToggleFavourite = {},
                            onSelectSeason = {},
                            onRetryEpisodes = {},
                            onOpenSeason = { opened += it.id },
                            onOpenEpisode = {},
                            onToggleEpisodeWatched = {},
                            onOpenTitle = {},
                            onOpenPerson = {},
                            onOpenLink = {},
                            onShare = { _, _ -> },
                        ),
                )
            }
        }
        return opened
    }

    @Test
    fun `a season of eight episodes has an All episodes button that opens the season`() {
        val opened = show(8)
        rule.onNodeWithText("All episodes").performClick()
        assertEquals(listOf("s1"), opened)
    }

    @Test
    fun `a season of five episodes lists them all and has no All episodes button`() {
        show(5)
        rule.onNodeWithText("Episode 5").assertExists()
        rule.onNodeWithText("All episodes").assertDoesNotExist()
    }
}
