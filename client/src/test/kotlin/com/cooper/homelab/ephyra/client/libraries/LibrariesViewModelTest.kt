package com.cooper.homelab.ephyra.client.libraries

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.LibraryKind
import com.cooper.homelab.ephyra.browse.LibraryRepository
import com.cooper.homelab.ephyra.browse.LibraryView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The Libraries tab's state: the movie and series libraries as tiles, and what each answer of the server turns into. */
class LibrariesViewModelTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Answers with [views], or throws [failure]; with [gate] set, waits for it first. */
    private class FakeLibraries(
        var views: List<LibraryView> = emptyList(),
        var failure: Throwable? = null,
        var gate: CompletableDeferred<Unit>? = null,
    ) : LibraryRepository {
        override suspend fun views(): List<LibraryView> {
            gate?.await()
            failure?.let { throw it }
            return views
        }
    }

    @After
    fun close() = scope.cancel()

    private fun viewModel(libraries: LibraryRepository) =
        LibrariesViewModel(scope, Dispatchers.Unconfined, libraries) { ServerFailure.Unreachable() }

    private val movies = LibraryView("lib-movies", "Movies", LibraryKind.Movies, primaryImageTag = "tag-1")
    private val shows = LibraryView("lib-shows", "Shows", LibraryKind.TvShows)
    private val boxSets = LibraryView("lib-sets", "Collections", LibraryKind.BoxSets)
    private val music = LibraryView("lib-music", "Music", LibraryKind.Other)

    @Test
    fun `nothing is shown before the libraries answer`() {
        val gate = CompletableDeferred<Unit>()
        val model = viewModel(FakeLibraries(listOf(movies), gate = gate))
        assertEquals(LibrariesUiState.Loading, model.state.value)
        model.start()
        assertEquals(LibrariesUiState.Loading, model.state.value)
        gate.complete(Unit)
        assertTrue(model.state.value is LibrariesUiState.Ready)
    }

    @Test
    fun `movie and series libraries are listed in the server's order and the rest are left out`() {
        val model = viewModel(FakeLibraries(listOf(shows, boxSets, movies, music)))
        model.start()
        val ready = model.state.value as LibrariesUiState.Ready
        assertEquals(
            listOf(
                LibraryTile("lib-shows", "Shows", LibraryKind.TvShows, imageTag = null),
                LibraryTile("lib-movies", "Movies", LibraryKind.Movies, imageTag = "tag-1"),
            ),
            ready.tiles,
        )
    }

    @Test
    fun `libraries that are none of ours are empty`() {
        val model = viewModel(FakeLibraries(listOf(boxSets, music)))
        model.start()
        assertEquals(LibrariesUiState.Empty, model.state.value)
    }

    @Test
    fun `no libraries at all are empty`() {
        val model = viewModel(FakeLibraries())
        model.start()
        assertEquals(LibrariesUiState.Empty, model.state.value)
    }

    @Test
    fun `a first read that fails is the failure, classified`() {
        val model = viewModel(FakeLibraries(failure = IOException("down")))
        model.start()
        assertEquals(LibrariesUiState.Error(ServerFailure.Unreachable()), model.state.value)
        assertFalse(model.refreshing.value)
    }

    @Test
    fun `a refresh after a failure tries again and lists what it finds`() {
        val libraries = FakeLibraries(failure = IOException("down"))
        val model = viewModel(libraries)
        model.start()
        libraries.failure = null
        libraries.views = listOf(movies)
        model.refresh()
        assertEquals(LibrariesUiState.Ready(listOf(LibraryTile("lib-movies", "Movies", LibraryKind.Movies, "tag-1"))), model.state.value)
    }

    @Test
    fun `a refresh keeps the tiles while it runs and shows the indicator`() {
        val libraries = FakeLibraries(listOf(movies))
        val model = viewModel(libraries)
        model.start()
        val gate = CompletableDeferred<Unit>()
        libraries.gate = gate
        libraries.views = listOf(movies, shows)
        model.refresh()
        assertTrue(model.refreshing.value)
        assertEquals(1, (model.state.value as LibrariesUiState.Ready).tiles.size)
        gate.complete(Unit)
        assertFalse(model.refreshing.value)
        assertEquals(2, (model.state.value as LibrariesUiState.Ready).tiles.size)
    }

    @Test
    fun `a refresh that fails keeps the tiles on screen`() {
        val libraries = FakeLibraries(listOf(movies))
        val model = viewModel(libraries)
        model.start()
        libraries.failure = IOException("down")
        model.refresh()
        assertEquals(1, (model.state.value as LibrariesUiState.Ready).tiles.size)
        assertFalse(model.refreshing.value)
    }

    @Test
    fun `a library that goes away on a refresh is gone from the tiles`() {
        val libraries = FakeLibraries(listOf(movies, shows))
        val model = viewModel(libraries)
        model.start()
        libraries.views = listOf(boxSets)
        model.refresh()
        assertEquals(LibrariesUiState.Empty, model.state.value)
    }
}
