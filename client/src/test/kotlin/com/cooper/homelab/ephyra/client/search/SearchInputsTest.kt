package com.cooper.homelab.ephyra.client.search

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.Page
import com.cooper.homelab.ephyra.browse.PersonResult
import com.cooper.homelab.ephyra.browse.SearchRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInputsTest {
    private class Recording : SearchRepository {
        val titleCalls = mutableListOf<List<Any>>()
        val peopleCalls = mutableListOf<List<Any>>()

        override suspend fun titles(
            term: String,
            kinds: Set<ItemKind>,
            startIndex: Int,
            limit: Int,
            withTotal: Boolean,
        ): Page<BrowseItem> {
            titleCalls += listOf(term, kinds, startIndex, limit, withTotal)
            return Page(listOf(BrowseItem("m1", "Heat", ItemKind.Movie)), null, startIndex)
        }

        override suspend fun people(
            term: String,
            startIndex: Int,
            limit: Int,
        ): Page<PersonResult> {
            peopleCalls += listOf(term, startIndex, limit)
            return Page(listOf(PersonResult("p1", "Al", null)), null, startIndex)
        }
    }

    @Test
    fun `a blank or one character term is not searched and the rest is trimmed`() {
        assertNull(SearchQuery.of("", SearchType.All))
        assertNull(SearchQuery.of("   ", SearchType.All))
        assertNull(SearchQuery.of(" a ", SearchType.All))
        assertEquals(SearchQuery("he", SearchType.Movies), SearchQuery.of("  he ", SearchType.Movies))
        assertEquals(MIN_SEARCH_LENGTH, 2)
    }

    @Test
    fun `each type asks for its own kinds and people ask for none`() {
        assertEquals(setOf(ItemKind.Movie, ItemKind.Series, ItemKind.Episode), SearchType.All.kinds)
        assertEquals(setOf(ItemKind.Movie), SearchType.Movies.kinds)
        assertEquals(setOf(ItemKind.Series), SearchType.Series.kinds)
        assertEquals(setOf(ItemKind.Episode), SearchType.Episodes.kinds)
        assertTrue(SearchType.People.kinds.isEmpty() && SearchType.People.isPeople)
        assertTrue(SearchType.entries.filter { it != SearchType.People }.none { it.isPeople })
    }

    @Test
    fun `a title search reads the repository with the term, the kinds and the page and asks for no count`() {
        val repo = Recording()
        val page = runBlocking { SearchQuery("heat", SearchType.Movies).read(repo, startIndex = 60, limit = 20) }
        assertEquals(listOf<Any>("heat", setOf(ItemKind.Movie), 60, 20, false), repo.titleCalls.single())
        assertTrue(repo.peopleCalls.isEmpty())
        assertEquals(60, page.startIndex)
        assertEquals("Heat", (page.items.single() as SearchHit.Title).item.name)
    }

    @Test
    fun `a people search reads people and wraps each as a person`() {
        val repo = Recording()
        val page = runBlocking { SearchQuery("al", SearchType.People).read(repo, startIndex = 0) }
        assertEquals("p1", (page.items.single() as SearchHit.Person).person.id)
        assertTrue(repo.titleCalls.isEmpty())
        assertEquals("al", repo.peopleCalls.single()[0])
    }
}
