package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryScrollBubbleTest {
    private fun film(
        name: String,
        year: Int? = null,
        sortName: String? = null,
    ) = BrowseItem(id = name, name = name, kind = ItemKind.Movie, year = year, sortName = sortName)

    private val titles =
        listOf(
            film("Alien", year = 1979, sortName = "alien"),
            film("The Long Quiet", year = 2021, sortName = "long quiet"),
            film("Éclair", year = 2015),
            film("1917", year = 2019, sortName = "1917"),
            film("¡Three Amigos!", year = 1986),
            film("Untitled"),
        )

    private fun bubble(
        sort: SortField,
        index: Int,
    ) = libraryBubbleText(sort, titles)?.invoke(index)

    @Test
    fun `ordered by name the bubble is the first letter of the sort name in capitals`() {
        assertEquals("A", bubble(SortField.Name, 0))
    }

    @Test
    fun `a title the server sorts without its leading article reads the letter it is sorted under`() {
        assertEquals("L", bubble(SortField.Name, 1))
    }

    @Test
    fun `a title with no sort name reads the first letter of its own name`() {
        assertEquals("É", bubble(SortField.Name, 2))
    }

    @Test
    fun `a name that starts with a digit or a symbol reads a hash`() {
        assertEquals(NON_LETTER_BUBBLE, bubble(SortField.Name, 3))
        assertEquals(NON_LETTER_BUBBLE, bubble(SortField.Name, 4))
    }

    @Test
    fun `a blank name has no bubble`() {
        assertNull(libraryBubbleText(SortField.Name, listOf(film("  ")))?.invoke(0))
    }

    @Test
    fun `ordered by release date the bubble is the year`() {
        assertEquals("1979", bubble(SortField.ReleaseDate, 0))
        assertEquals("2021", bubble(SortField.ReleaseDate, 1))
    }

    @Test
    fun `a title with no year has no bubble when ordered by release date`() {
        assertNull(bubble(SortField.ReleaseDate, 5))
    }

    @Test
    fun `an index past the titles has no bubble in either order`() {
        assertNull(bubble(SortField.Name, titles.size))
        assertNull(bubble(SortField.ReleaseDate, titles.size))
        assertNull(bubble(SortField.Name, -1))
    }

    @Test
    fun `any other order has no bubble`() {
        val others = SortField.entries - SortField.Name - SortField.ReleaseDate
        others.forEach { assertNull(it.name, libraryBubbleText(it, titles)) }
    }

    @Test
    fun `an empty library still gives a bubble function that answers null`() {
        val none = libraryBubbleText(SortField.Name, emptyList())
        assertNotNull(none)
        assertNull(none?.invoke(0))
    }
}
