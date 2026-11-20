package com.cooper.homelab.ephyra.client.libraries

import androidx.activity.ComponentActivity
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.FilterChoices
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.ItemUserData
import com.cooper.homelab.ephyra.browse.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A library's grid for each state, on a phone: the titles, the order and filters above them, and what a tap or a chip reports. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class LibraryGridScreenTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val heat = BrowseItem("m1", "Heat", ItemKind.Movie, year = 1995)
    private val dark = BrowseItem("s1", "Dark", ItemKind.Series, year = 2017, userData = ItemUserData(unplayedCount = 3))
    private val controls = LibraryGridControls("Movies", LibraryFilterDraft(), null)

    private class Reported {
        val opened = mutableListOf<Pair<ItemKind, String>>()
        val applied = mutableListOf<LibraryFilterDraft>()
        val cleared = mutableListOf<String>()
        var retried = 0
        var clearedAll = 0
        var filtersOpened = 0
        var loadedMore = 0
        var back = 0
    }

    private fun show(
        state: LibraryGridUiState,
        reported: Reported = Reported(),
    ): Reported {
        rule.setContent {
            BingeExpressiveTheme {
                LibraryGridScreen(
                    state = state,
                    imageUrl = { _, _ -> null },
                    actions =
                        LibraryGridActions(
                            onOpenItem = { kind, id -> reported.opened += kind to id },
                            onLoadMore = { reported.loadedMore++ },
                            onRetry = { reported.retried++ },
                            onBack = { reported.back++ },
                            onFiltersOpened = { reported.filtersOpened++ },
                            onApply = { reported.applied += it },
                            onClearChip = { reported.cleared += it },
                            onClearFilters = { reported.clearedAll++ },
                        ),
                )
            }
        }
        rule.waitForIdle()
        return reported
    }

    private fun ready(
        controls: LibraryGridControls = this.controls,
        items: List<BrowseItem> = listOf(heat, dark),
    ) = GridState.Ready(controls, items, hasMore = false, appending = false, appendFailed = false)

    @Test
    fun `the library's name is the title and each title is a card that opens by its kind`() {
        val reported = show(ready())
        rule.onNodeWithText("Movies").assertIsDisplayed()
        rule.onNodeWithText("Heat", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Dark", substring = true).performClick()
        assertEquals(listOf(ItemKind.Series to "s1"), reported.opened)
    }

    @Test
    fun `a library whose name is not known yet is titled Libraries`() {
        show(GridState.Loading(LibraryGridControls(null, LibraryFilterDraft(), null)))
        rule.onNodeWithText("Libraries").assertIsDisplayed()
    }

    @Test
    fun `an empty library says so and an empty result of a filter offers to clear it`() {
        show(GridState.Empty(controls))
        rule.onNodeWithText("This library has nothing in it yet.").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun `filters that match nothing offer Clear filters`() {
        val filtered = LibraryGridControls("Movies", LibraryFilterDraft(filters = ItemFilters(genres = setOf("Drama"))), null)
        val reported = show(GridState.Empty(filtered))
        rule.onNodeWithText("No title matches these filters.").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").performClick()
        assertEquals(1, reported.clearedAll)
    }

    @Test
    fun `a failure another go can fix offers Try again`() {
        val reported = show(GridState.Error(controls, ServerFailure.Unreachable()))
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, reported.retried)
    }

    @Test
    fun `a failure no retry can fix offers none`() {
        show(GridState.Error(controls, ServerFailure.NotPermitted))
        rule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `an applied filter is a chip that clears itself, and the pill counts it`() {
        val draft = LibraryFilterDraft(filters = ItemFilters(genres = setOf("Drama", "Action"), played = true))
        val reported = show(ready(LibraryGridControls("Movies", draft, null)))
        rule.onNodeWithText("Action, Drama").performClick()
        rule.onNodeWithText("Watched").performClick()
        assertEquals(listOf(LibraryFilterSection.GENRES, LibraryFilterSection.PLAYED), reported.cleared)
    }

    @Test
    fun `the pill opens the sheet, which tells the model and lists the order and the filters`() {
        val choices = FilterChoices(listOf("Drama"), listOf(2020, 1999), listOf("PG"))
        val reported = show(ready(LibraryGridControls("Movies", LibraryFilterDraft(), choices)))
        rule.onNodeWithContentDescription("Filters and order").performClick()
        assertEquals(1, reported.filtersOpened)
        rule.onNodeWithText("Sort by").assertIsDisplayed()
        rule.onNodeWithText("Date added").assertIsDisplayed()
        // The long filters are rows below the fold of a sheet that opens part-way: they are in the sheet, not necessarily in view.
        rule.onNodeWithText("Genres").assertExists()
        rule.onNodeWithText("Years").assertExists()
    }

    @Test
    fun `a choice in the sheet is applied with the order and filters as one change`() {
        val reported =
            show(ready(LibraryGridControls("Movies", LibraryFilterDraft(), FilterChoices(emptyList(), emptyList(), listOf("PG")))))
        rule.onNodeWithContentDescription("Filters and order").performClick()
        rule.onNodeWithText("Date added").performClick()
        rule.onNodeWithText("PG").performClick()
        rule.onNodeWithText("Show results").performClick()
        assertEquals(
            listOf(LibraryFilterDraft(ItemSort(SortField.DateAdded), ItemFilters(officialRatings = setOf("PG")))),
            reported.applied,
        )
    }

    @Test
    fun `genres are chosen on a page of their own that has a search, and apply with the rest`() {
        val choices = FilterChoices(listOf("Drama", "Action"), emptyList(), emptyList())
        val reported = show(ready(LibraryGridControls("Movies", LibraryFilterDraft(), choices)))
        rule.onNodeWithContentDescription("Filters and order").performClick()
        rule.onNodeWithText("Genres").performScrollTo().performClick()
        rule.onNodeWithText("Search genres").assertIsDisplayed()
        rule.onNodeWithText("Drama").performClick()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Show results").performClick()
        assertEquals(listOf(LibraryFilterDraft(filters = ItemFilters(genres = setOf("Drama")))), reported.applied)
    }

    @Test
    fun `the Back arrow reports`() {
        val reported = show(ready())
        rule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, reported.back)
    }

    @Test
    fun `a title key names its kind and id and nothing else is read as one`() {
        assertEquals("Movie:m1", libraryItemKey(heat))
        assertEquals(ItemKind.Series to "s1", parseLibraryItemKey(libraryItemKey(dark)))
        assertNull(parseLibraryItemKey("nonsense"))
    }

    @Test
    fun `the order and filters are saved as plain text and come back the same`() {
        val draft =
            LibraryFilterDraft(
                ItemSort(SortField.Rating, ascending = false),
                ItemFilters(
                    genres = setOf("Drama"),
                    years = setOf(2020),
                    officialRatings = setOf("PG"),
                    played = false,
                    favouritesOnly = true,
                ),
            )
        val saved = with(LibraryFilterDraftSaver) { SaverScope { true }.save(draft) }
        assertNotNull(saved)
        assertEquals(draft, LibraryFilterDraftSaver.restore(saved!!))
        val plain = with(LibraryFilterDraftSaver) { SaverScope { true }.save(LibraryFilterDraft()) }
        assertEquals(LibraryFilterDraft(), LibraryFilterDraftSaver.restore(plain!!))
    }
}
