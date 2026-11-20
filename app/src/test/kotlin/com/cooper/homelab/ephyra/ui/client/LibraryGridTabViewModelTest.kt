package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.presentation.paging.GridState
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.client.libraries.LibraryFilterDraft
import com.cooper.homelab.ephyra.client.libraries.LibraryGridUiState
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A library's grid host: it reads the signed-in client, builds the grid's model on it, and hands the screen picture URLs with no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryGridTabViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun viewModel() =
        teardown
            .made(
                LibraryGridTabViewModel(
                    connection,
                    Dispatchers.IO,
                    JellyfinServerProfile.from("10.11.6"),
                    "lib-sets",
                    LibraryFilterDraft(),
                ),
            ).also { it.start() }

    private fun LibraryGridTabViewModel.settled(matches: (LibraryGridUiState) -> Boolean) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }

    private fun seed() {
        jellyfin.userViews = """[{"Id":"lib-sets","Name":"Collections","CollectionType":"boxsets"}]"""
        runBlocking { sessions.save(jellyfin.session()) }
    }

    private fun addPart(
        id: String,
        year: Int,
    ) = jellyfin.browse.titles.add(FakeTitle(id, "Film $id", "set-1", "Movie", year = year))

    private fun GridState<*, BrowseItem>.items() = (this as GridState.Ready).items

    @Test
    fun `a box set with no picture of its own is drawn with its first part's poster, with no token in the URL`() {
        seed()
        jellyfin.browse.titles.add(FakeTitle("set-1", "Bond", "lib-sets", "BoxSet", hasPicture = false))
        addPart("film-late", 2012)
        addPart("film-early", 1962)
        val vm = viewModel()
        val ready = vm.settled { it is GridState.Ready && it.controls.posters.isNotEmpty() }
        val box = ready.items().single()
        assertEquals(ItemKind.BoxSet, box.kind)
        assertNull("it has no picture of its own", box.imageTags[ImageKind.Primary])
        val url = vm.imageUrl(box, 400).orEmpty()
        assertTrue(
            url,
            url.contains("/Items/film-early/Images/Primary") && url.contains("tag=p-film-early") && url.contains("maxWidth=400"),
        )
        assertFalse(url, url.contains("token-1"))
    }

    @Test
    fun `a box set with a picture of its own keeps it, and nothing is read for it`() {
        seed()
        jellyfin.browse.titles.add(FakeTitle("set-1", "Bond", "lib-sets", "BoxSet"))
        addPart("film-early", 1962)
        val vm = viewModel()
        val box = vm.settled { it is GridState.Ready }.items().single()
        val url = vm.imageUrl(box, 400).orEmpty()
        assertTrue(url, url.contains("/Items/set-1/Images/Primary") && url.contains("tag=p-set-1"))
        assertTrue(
            vm
                .settled { it is GridState.Ready }
                .controls.posters
                .isEmpty(),
        )
        assertFalse(
            "no read of its parts",
            jellyfin.browse.itemRequests.any { it.url.queryParameter("parentId") == "set-1" },
        )
    }

    @Test
    fun `a box set whose parts have no picture either has none to draw`() {
        seed()
        jellyfin.browse.titles.add(FakeTitle("set-1", "Bond", "lib-sets", "BoxSet", hasPicture = false))
        jellyfin.browse.titles.add(FakeTitle("film-1", "Film", "set-1", "Movie", hasPicture = false))
        val vm = viewModel()
        val box = vm.settled { it is GridState.Ready }.items().single()
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.browse.itemRequests.none { it.url.queryParameter("parentId") == "set-1" }) delay(POLL_MILLIS)
            }
        }
        assertNull(vm.imageUrl(box, 400))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
    }
}
