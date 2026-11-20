package com.cooper.homelab.ephyra.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.client.search.SearchKind
import org.junit.Assert.assertEquals
import org.junit.Test

/** A search result opens the page of what it is: a movie, a series, an episode or a person. */
class SearchHitRoutesTest {
    private fun opened(
        kind: SearchKind,
        id: String,
    ): List<NavKey> = NavBackStack<NavKey>(ShellRoute).also { it.openSearchHit(kind, id) }.toList()

    @Test
    fun `each kind of result opens its own page over the shell`() {
        assertEquals(listOf(ShellRoute, MovieRoute("m1")), opened(SearchKind.Movie, "m1"))
        assertEquals(listOf(ShellRoute, SeriesRoute("s1")), opened(SearchKind.Series, "s1"))
        assertEquals(listOf(ShellRoute, EpisodeRoute("e1")), opened(SearchKind.Episode, "e1"))
        assertEquals(listOf(ShellRoute, PersonRoute("p1")), opened(SearchKind.Person, "p1"))
    }
}
