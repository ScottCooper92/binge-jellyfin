package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.ephyra.browse.FilterChoices
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterDraftTest {
    private val choices =
        FilterChoices(genres = listOf("Action", "Drama"), years = listOf(2020, 1999), officialRatings = listOf("PG", "15"))

    private fun section(
        draft: LibraryFilterDraft,
        id: String,
        with: FilterChoices = choices,
    ) = draft.sections(with).firstOrNull { it.id == id }

    @Test
    fun `a new draft is the library's default order with nothing filtered`() {
        val draft = LibraryFilterDraft()
        assertEquals(0, draft.appliedCount)
        assertEquals(
            FilterSection.Single(
                LibraryFilterSection.SORT,
                librarySortFields.map {
                    it.name
                },
                "Name",
            ),
            section(draft, LibraryFilterSection.SORT),
        )
        assertEquals("ascending", (section(draft, LibraryFilterSection.DIRECTION) as FilterSection.Single).selected)
        assertEquals("Any", (section(draft, LibraryFilterSection.PLAYED) as FilterSection.Single).selected)
        assertEquals("all", (section(draft, LibraryFilterSection.FAVOURITES) as FilterSection.Single).selected)
    }

    @Test
    fun `choices sections list the library's values and a section with none is left out`() {
        assertEquals(listOf("Action", "Drama"), (section(LibraryFilterDraft(), LibraryFilterSection.GENRES) as FilterSection.Multi).options)
        assertEquals(listOf("2020", "1999"), (section(LibraryFilterDraft(), LibraryFilterSection.YEARS) as FilterSection.Multi).options)
        val empty = FilterChoices(emptyList(), emptyList(), emptyList())
        assertNull(section(LibraryFilterDraft(), LibraryFilterSection.GENRES, empty))
        assertNull(section(LibraryFilterDraft(), LibraryFilterSection.YEARS, empty))
        assertNull(section(LibraryFilterDraft(), LibraryFilterSection.RATINGS, empty))
        assertTrue(section(LibraryFilterDraft(), LibraryFilterSection.SORT, empty) != null)
    }

    @Test
    fun `picking a sort field and a direction changes the order and no filter`() {
        val draft = LibraryFilterDraft().select(LibraryFilterSection.SORT, "Rating").select(LibraryFilterSection.DIRECTION, "descending")
        assertEquals(ItemSort(SortField.Rating, ascending = false), draft.sort)
        assertEquals(0, draft.appliedCount)
    }

    @Test
    fun `a sort field the sheet does not offer, or an unknown option, changes nothing`() {
        val draft = LibraryFilterDraft()
        assertEquals(draft, draft.select(LibraryFilterSection.SORT, "Random"))
        assertEquals(draft, draft.select(LibraryFilterSection.SORT, "Nonsense"))
        assertEquals(draft, draft.select("unknown", "x"))
        assertEquals(draft, draft.select(LibraryFilterSection.DIRECTION, "sideways"))
    }

    @Test
    fun `the watched choice maps to the server's played flag and back`() {
        val watched = LibraryFilterDraft().select(LibraryFilterSection.PLAYED, "Watched")
        assertEquals(true, watched.filters.played)
        assertEquals("Watched", (section(watched, LibraryFilterSection.PLAYED) as FilterSection.Single).selected)
        assertEquals(false, watched.select(LibraryFilterSection.PLAYED, "Unwatched").filters.played)
        assertNull(watched.select(LibraryFilterSection.PLAYED, "Any").filters.played)
    }

    @Test
    fun `favourites only is a filter and counts as one`() {
        val draft = LibraryFilterDraft().select(LibraryFilterSection.FAVOURITES, "only")
        assertTrue(draft.filters.favouritesOnly)
        assertEquals(1, draft.appliedCount)
        assertFalse(draft.select(LibraryFilterSection.FAVOURITES, "all").filters.favouritesOnly)
    }

    @Test
    fun `tapping a value of a multi choice chooses it and tapping it again drops it`() {
        val one = LibraryFilterDraft().toggle(LibraryFilterSection.GENRES, "Drama").toggle(LibraryFilterSection.GENRES, "Action")
        assertEquals(setOf("Drama", "Action"), one.filters.genres)
        assertEquals(setOf("Drama"), one.toggle(LibraryFilterSection.GENRES, "Action").filters.genres)
        val years = LibraryFilterDraft().toggle(LibraryFilterSection.YEARS, "2020")
        assertEquals(setOf(2020), years.filters.years)
        assertEquals(setOf("2020"), (section(years, LibraryFilterSection.YEARS) as FilterSection.Multi).chosen)
        assertEquals(years, years.toggle(LibraryFilterSection.YEARS, "not a year"))
        assertEquals(setOf("PG"), LibraryFilterDraft().toggle(LibraryFilterSection.RATINGS, "PG").filters.officialRatings)
        assertEquals(years, years.toggle("unknown", "x"))
    }

    @Test
    fun `each filter set counts once however many values it holds, and clear all keeps the order`() {
        val draft =
            LibraryFilterDraft(ItemSort(SortField.DateAdded, false))
                .toggle(LibraryFilterSection.GENRES, "Drama")
                .toggle(LibraryFilterSection.GENRES, "Action")
                .toggle(LibraryFilterSection.YEARS, "2020")
                .toggle(LibraryFilterSection.RATINGS, "PG")
                .select(LibraryFilterSection.PLAYED, "Unwatched")
                .select(LibraryFilterSection.FAVOURITES, "only")
        assertEquals(5, draft.appliedCount)
        val cleared = draft.clearedFilters()
        assertEquals(ItemFilters(), cleared.filters)
        assertEquals(ItemSort(SortField.DateAdded, false), cleared.sort)
        assertEquals(0, cleared.appliedCount)
    }

    @Test
    fun `the order counts as one active setting and clear all resets it with the filters`() {
        val sortOnly = LibraryFilterDraft(ItemSort(SortField.Rating, false))
        assertEquals(0, sortOnly.appliedCount)
        assertEquals(1, sortOnly.activeCount)
        assertEquals(LibraryFilterDraft(), sortOnly.clearedAll())
        assertEquals(0, sortOnly.clearedAll().activeCount)
        val both = sortOnly.toggle(LibraryFilterSection.GENRES, "Drama")
        assertEquals(2, both.activeCount)
        assertEquals(0, both.clearedAll().activeCount)
        assertEquals(0, LibraryFilterDraft().activeCount)
    }

    @Test
    fun `a sort the sheet does not list shows the first one as chosen`() {
        val draft = LibraryFilterDraft(ItemSort(SortField.Random))
        assertEquals("Name", (section(draft, LibraryFilterSection.SORT) as FilterSection.Single).selected)
    }
}
