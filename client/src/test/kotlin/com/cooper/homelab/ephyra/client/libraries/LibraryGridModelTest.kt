package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.FilterChoices
import com.cooper.homelab.ephyra.browse.ItemFilters
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.ItemSort
import com.cooper.homelab.ephyra.browse.ItemsRepository
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.LibraryRepository
import com.cooper.homelab.ephyra.browse.LibraryView
import com.cooper.homelab.ephyra.browse.Page
import com.cooper.homelab.ephyra.browse.SortField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** One library's grid: the titles paged by start index, a change of order or filters starting again, and what each failure turns into. */
class LibraryGridModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val movies = LibraryView("lib-movies", "Movies", LibraryKind.Movies)

    /** What the server holds: [all] titles, read by start index; every read is recorded in [reads]. */
    private class FakeItems(
        var all: List<BrowseItem> = emptyList(),
        var failure: Throwable? = null,
        var choices: FilterChoices = FilterChoices(listOf("Drama"), listOf(2020), listOf("PG")),
        var choicesFailure: Throwable? = null,
    ) : ItemsRepository {
        val reads = mutableListOf<Read>()
        var choiceReads = 0

        class Read(
            val sort: ItemSort,
            val filters: ItemFilters,
            val startIndex: Int,
        )

        override suspend fun items(
            library: LibraryView,
            sort: ItemSort,
            filters: ItemFilters,
            startIndex: Int,
            limit: Int,
            withTotal: Boolean,
        ): Page<BrowseItem> {
            reads += Read(sort, filters, startIndex)
            failure?.let { throw it }
            return Page(all.drop(startIndex).take(limit), total = all.size, startIndex = startIndex)
        }

        override suspend fun filterChoices(library: LibraryView): FilterChoices {
            choiceReads++
            choicesFailure?.let { throw it }
            return choices
        }
    }

    private class FakeLibraries(
        var views: List<LibraryView>,
    ) : LibraryRepository {
        var reads = 0

        override suspend fun views(): List<LibraryView> {
            reads++
            return views
        }
    }

    private val items = FakeItems(all = titles(5))
    private val libraries = FakeLibraries(listOf(movies))

    @After
    fun close() = scope.cancel()

    private fun model(initial: LibraryFilterDraft = LibraryFilterDraft()) =
        LibraryGridModel(
            scope = scope,
            dispatcher = Dispatchers.Unconfined,
            libraryId = "lib-movies",
            libraries = libraries,
            items = items,
            classify = { ServerFailure.Unreachable() },
            initial = initial,
            pageSize = PAGE,
        ).also { it.state.onEach { }.launchIn(scope) }

    private fun titles(count: Int) = List(count) { BrowseItem("item-$it", "Title $it", ItemKind.Movie) }

    private fun LibraryGridModel.ready() = state.value as GridState.Ready

    @Test
    fun `the first page is read from the start and the title is the library's name`() {
        val model = model()
        val ready = model.ready()
        assertEquals(listOf("item-0", "item-1"), ready.items.map { it.id })
        assertTrue(ready.hasMore)
        assertEquals("Movies", ready.controls.libraryName)
        assertEquals(LibraryFilterDraft(), ready.controls.applied)
        assertEquals(listOf(0), items.reads.map { it.startIndex })
    }

    @Test
    fun `more pages are read from where the last ended, and the last page ends the list`() {
        val model = model()
        model.loadMore()
        assertEquals(listOf("item-0", "item-1", "item-2", "item-3"), model.ready().items.map { it.id })
        model.loadMore()
        val last = model.ready()
        assertEquals(5, last.items.size)
        assertFalse(last.hasMore)
        assertEquals(listOf(0, 2, 4), items.reads.map { it.startIndex })
    }

    @Test
    fun `applying an order and filters starts again from the first page, once`() {
        val model = model()
        model.loadMore()
        items.reads.clear()
        val draft = LibraryFilterDraft().select(LibraryFilterSection.SORT, "Rating").toggle(LibraryFilterSection.GENRES, "Drama")
        model.apply(draft)
        assertEquals(1, items.reads.size)
        assertEquals(0, items.reads.single().startIndex)
        assertEquals(ItemSort(SortField.Rating), items.reads.single().sort)
        assertEquals(
            setOf("Drama"),
            items.reads
                .single()
                .filters.genres,
        )
        assertEquals(draft, model.ready().controls.applied)
        assertEquals(2, model.ready().items.size)
    }

    @Test
    fun `a library opened with filters reads them on its first page`() {
        val draft = LibraryFilterDraft(ItemSort(SortField.DateAdded, false), ItemFilters(played = false, favouritesOnly = true))
        val model = model(draft)
        assertEquals(draft.filters, items.reads.single().filters)
        assertEquals(draft.sort, items.reads.single().sort)
        assertEquals(draft, model.ready().controls.applied)
    }

    @Test
    fun `a filter that matches nothing is an empty grid that still carries its filters`() {
        items.all = emptyList()
        val model = model(LibraryFilterDraft(filters = ItemFilters(genres = setOf("Drama"))))
        val empty = model.state.value as GridState.Empty
        assertEquals(setOf("Drama"), empty.controls.applied.filters.genres)
        assertEquals("Movies", empty.controls.libraryName)
    }

    @Test
    fun `clearing a chip drops that filter and keeps the rest and the order`() {
        val draft =
            LibraryFilterDraft(ItemSort(SortField.Rating, false))
                .toggle(LibraryFilterSection.GENRES, "Drama")
                .select(LibraryFilterSection.PLAYED, "Watched")
        val model = model(draft)
        model.clearChip(LibraryFilterSection.GENRES)
        val applied = model.ready().controls.applied
        assertTrue(applied.filters.genres.isEmpty())
        assertEquals(true, applied.filters.played)
        assertEquals(ItemSort(SortField.Rating, false), applied.sort)
    }

    @Test
    fun `clearing the filters keeps the order`() {
        val model = model(LibraryFilterDraft(ItemSort(SortField.Runtime)).toggle(LibraryFilterSection.YEARS, "2020"))
        model.clearFilters()
        val applied = model.ready().controls.applied
        assertEquals(ItemFilters(), applied.filters)
        assertEquals(ItemSort(SortField.Runtime), applied.sort)
    }

    @Test
    fun `a first page that fails is a failure with the controls, and retry reads it again`() {
        items.failure = IOException("down")
        val model = model()
        val failed = model.state.value as GridState.Error
        assertEquals(ServerFailure.Unreachable(), failed.failure)
        assertEquals(LibraryFilterDraft(), failed.controls.applied)
        items.failure = null
        model.retry()
        assertEquals(2, model.ready().items.size)
    }

    @Test
    fun `a page that fails after the first keeps what is shown and says so`() {
        val model = model()
        items.failure = IOException("down")
        model.loadMore()
        val shown = model.ready()
        assertEquals(2, shown.items.size)
        assertTrue(shown.appendFailed)
        items.failure = null
        model.loadMore()
        assertEquals(4, model.ready().items.size)
        assertFalse(model.ready().appendFailed)
    }

    @Test
    fun `refreshing reads the first page again and the rows stay until it answers`() {
        val model = model()
        items.all = titles(1)
        model.refresh()
        assertEquals(1, model.ready().items.size)
        assertFalse(model.ready().refreshing)
        assertFalse(model.refreshing.value)
    }

    @Test
    fun `a refresh that fails keeps the rows and says so, and the next refresh clears it`() {
        val model = model()
        items.failure = IOException("down")
        model.refresh()
        val shown = model.ready()
        assertEquals(PAGE, shown.items.size)
        assertTrue(shown.refreshFailed)
        assertFalse(shown.refreshing)
        items.failure = null
        model.refresh()
        assertFalse(model.ready().refreshFailed)
    }

    @Test
    fun `a library the user cannot see is not found`() {
        libraries.views = emptyList()
        val model = model()
        assertEquals(ServerFailure.Unreachable(), (model.state.value as GridState.Error).failure)
        assertTrue(items.reads.isEmpty())
    }

    @Test
    fun `the controls carry what the library holds once it has been read`() {
        libraries.views = listOf(LibraryView("lib-movies", "Collections", LibraryKind.BoxSets))
        val model = model()
        assertEquals(LibraryKind.BoxSets, model.ready().controls.kind)
    }

    @Test
    fun `starting reads the library once, so the first page does not read it again`() {
        val model =
            LibraryGridModel(
                scope,
                Dispatchers.Unconfined,
                "lib-movies",
                libraries,
                items,
                { ServerFailure.Unreachable() },
                pageSize = PAGE,
            )
        model.start()
        assertEquals(1, libraries.reads)
        model.state.onEach { }.launchIn(scope)
        assertEquals("Movies", model.state.value.controls.libraryName)
        assertEquals(2, model.ready().items.size)
        assertEquals(1, libraries.reads)
    }

    @Test
    fun `the filter choices are read when the sheet first opens, and not again`() {
        val model = model()
        assertNull(model.ready().controls.choices)
        model.filtersOpened()
        assertEquals(
            listOf("Drama"),
            model
                .ready()
                .controls.choices
                ?.genres,
        )
        model.filtersOpened()
        assertEquals(1, items.choiceReads)
    }

    @Test
    fun `choices that cannot be read leave the sheet with the fixed filters, and the next open tries again`() {
        items.choicesFailure = IOException("down")
        val model = model()
        model.filtersOpened()
        assertNull(model.ready().controls.choices)
        items.choicesFailure = null
        model.filtersOpened()
        assertEquals(2, items.choiceReads)
        assertEquals(
            listOf(2020),
            model
                .ready()
                .controls.choices
                ?.years,
        )
    }

    private companion object {
        const val PAGE = 2
    }
}
