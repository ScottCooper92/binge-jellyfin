package com.cooper.binge.companion.jellyfin.ui.search

import com.cooper.homelab.core.presentation.search.MAX_RECENT
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRecentSearchesTest {
    private val recent = LocalRecentSearches(InMemoryDataStore())

    @Test
    fun `it starts empty`() = runBlocking { assertEquals(emptyList<String>(), recent.ids.first()) }

    @Test
    fun `the newest is first, and one opened again moves up rather than repeating`() =
        runBlocking {
            listOf("a", "b", "c", "a").forEach { recent.remember(it) }
            assertEquals(listOf("a", "c", "b"), recent.ids.first())
        }

    @Test
    fun `only the last few stay`() =
        runBlocking {
            (1..MAX_RECENT + 3).forEach { recent.remember("e$it") }
            assertEquals((MAX_RECENT + 3 downTo 4).map { "e$it" }, recent.ids.first())
        }

    @Test
    fun `clear forgets them all`() =
        runBlocking {
            recent.remember("a")
            recent.clear()
            assertEquals(emptyList<String>(), recent.ids.first())
        }
}
