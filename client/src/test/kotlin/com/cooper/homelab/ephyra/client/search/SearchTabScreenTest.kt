package com.cooper.homelab.ephyra.client.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.PersonResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Search tab for each state, on a phone: the rows and their pictures, the chips, and what a tap or a chip reports. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class SearchTabScreenTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val movie = BrowseItem("m1", "Heat", ItemKind.Movie, year = 1995, userData = ItemUserData(played = true, favourite = true))
    private val series = BrowseItem("s1", "Dark", ItemKind.Series, year = 2017)
    private val episode = BrowseItem("e1", "Pilot", ItemKind.Episode, seriesName = "Dark", seasonNumber = 1, episodeNumber = 2)
    private val person = PersonResult("p1", "Al Pacino", null)
    private val hits = listOf(SearchHit.Title(movie), SearchHit.Title(series), SearchHit.Title(episode), SearchHit.Person(person))

    private class Reported {
        val opened = mutableListOf<Pair<SearchKind, String>>()
        val typed = mutableListOf<String>()
        val types = mutableListOf<SearchType>()
        val picked = mutableListOf<String>()
        var retried = 0
        var cleared = 0
        var loadedMore = 0
    }

    private fun show(
        body: SearchBody,
        text: String = "",
        type: SearchType = SearchType.All,
        asked: MutableList<Pair<SearchKind, Int>> = mutableListOf(),
    ): Reported {
        val reported = Reported()
        rule.setContent {
            BingeExpressiveTheme {
                SearchTabScreen(
                    state = SearchUiState(text, type, body),
                    imageUrl = { hit, width ->
                        asked += hit.kind to width
                        null
                    },
                    actions =
                        SearchActions(
                            onQueryChange = { reported.typed += it },
                            onClear = { reported.cleared++ },
                            onSubmit = {},
                            onOpen = { kind, id -> reported.opened += kind to id },
                            onLoadMore = { reported.loadedMore++ },
                            onRetry = { reported.retried++ },
                            onType = { reported.types += it },
                            onPickRecent = { reported.picked += it },
                            onClearRecents = {},
                        ),
                )
            }
        }
        rule.waitForIdle()
        return reported
    }

    private fun ready(list: List<SearchHit> = hits) =
        SearchBody.Ready(list, list.size, hasMore = false, appending = false, appendFailed = false)

    @Test
    fun `idle offers the recent queries, and a tap on one reports it`() {
        val reported = show(SearchBody.Idle(listOf("alien", "heat")))
        rule.onNodeWithText("alien").performClick()
        assertEquals(listOf("alien"), reported.picked)
    }

    @Test
    fun `idle with nothing to offer says what can be searched`() {
        show(SearchBody.Idle(emptyList()))
        rule.onNodeWithText("Find a movie, a series, an episode or a person.").assertIsDisplayed()
    }

    @Test
    fun `each result is a row with its name and subtitle, and a tap opens it by its kind`() {
        val reported = show(ready(), "dark", SearchType.All)
        rule.onNodeWithText("Heat").assertIsDisplayed()
        rule.onNodeWithText("Movie · 1995").assertIsDisplayed()
        rule.onNodeWithText("Series · 2017").assertIsDisplayed()
        rule.onNodeWithText("Dark · S1, E2").assertIsDisplayed()
        rule.onNodeWithText("Al Pacino").assertIsDisplayed()
        rule.onNodeWithText("Pilot").performClick()
        rule.onNodeWithText("Al Pacino").performClick()
        assertEquals(listOf(SearchKind.Episode to "e1", SearchKind.Person to "p1"), reported.opened)
    }

    @Test
    fun `a picture is asked for in the size its row draws`() {
        val asked = mutableListOf<Pair<SearchKind, Int>>()
        show(ready(), asked = asked)
        assertEquals(setOf(SearchKind.Movie, SearchKind.Series, SearchKind.Episode, SearchKind.Person), asked.map { it.first }.toSet())
        assertEquals(setOf(300, 400, 200), asked.map { it.second }.toSet())
    }

    @Test
    fun `the type chips report the type chosen`() {
        val reported = show(ready(), "dark")
        rule.onNodeWithText("People").performClick()
        rule.onNodeWithText("Episodes").performClick()
        assertEquals(listOf(SearchType.People, SearchType.Episodes), reported.types)
    }

    @Test
    fun `a type with none of the results so far says so`() {
        show(ready(emptyList()), "dark", SearchType.People)
        rule.onNodeWithText("None of the results so far is of this kind.").assertIsDisplayed()
    }

    @Test
    fun `a search that finds nothing names the query and keeps the chips`() {
        show(SearchBody.NoResults("zzz"), "zzz")
        rule.onNodeWithText("Nothing matches “zzz”.").assertIsDisplayed()
        rule.onNodeWithText("People").assertIsDisplayed()
    }

    @Test
    fun `a failure another go can fix offers Try again`() {
        val reported = show(SearchBody.Failed(ServerFailure.Unreachable()), "dark")
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, reported.retried)
    }

    @Test
    fun `a failure no retry can fix offers none`() {
        show(SearchBody.Failed(ServerFailure.NotPermitted), "dark")
        rule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `loading shows no result`() {
        show(SearchBody.Loading, "dark")
        rule.onNodeWithText("Heat").assertDoesNotExist()
    }
}
