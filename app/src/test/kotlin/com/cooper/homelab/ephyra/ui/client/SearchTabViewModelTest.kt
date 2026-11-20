package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.search.QueryHistory
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.PersonResult
import com.cooper.homelab.ephyra.client.search.SearchBody
import com.cooper.homelab.ephyra.client.search.SearchHit
import com.cooper.homelab.ephyra.client.search.SearchType
import com.cooper.homelab.ephyra.client.search.SearchUiState
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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

/** The Search tab's host: it reads the signed-in client, builds the tab's model on it, and hands the screen picture URLs with no token. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchTabViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))
    private val history =
        object : QueryHistory {
            override val recents = MutableStateFlow(listOf("alien"))

            override suspend fun remember(query: String) = Unit

            override suspend fun clear() = Unit
        }

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun viewModel(initialQuery: String = "") =
        teardown
            .made(SearchTabViewModel(connection, Dispatchers.IO, JellyfinServerProfile.from("10.11.6"), history, initialQuery))
            .also { it.start() }

    /** The model is built once the client is read, and its first state is the recent queries: before that the tab shows an empty idle. */
    private fun SearchTabViewModel.modelReady() = settled { (it.body as? SearchBody.Idle)?.recents == listOf("alien") }

    private fun SearchTabViewModel.settled(matches: (SearchUiState) -> Boolean) =
        try {
            runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(matches) } }
        } catch (_: TimeoutCancellationException) {
            throw AssertionError(
                "The state never matched; it was ${state.value}; asked ${jellyfin.requests.map {
                    it.url.encodedPath + "?" + it.url.encodedQuery
                }.takeLast(
                    4,
                )}",
            )
        }

    @Test
    fun `with a session it offers the recent queries before anything is typed`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val idle = viewModel().settled { (it.body as? SearchBody.Idle)?.recents == listOf("alien") }
        assertEquals(SearchType.All, idle.type)
    }

    @Test
    fun `with no session it is not connected, and a retry reads the session again`() {
        val vm = viewModel()
        assertEquals(SearchBody.Failed(ServerFailure.NotConnected), vm.settled { it.body is SearchBody.Failed }.body)
        runBlocking { sessions.save(jellyfin.session()) }
        vm.retry()
        assertTrue(vm.settled { it.body is SearchBody.Idle }.body is SearchBody.Idle)
    }

    @Test
    fun `typing searches the user's server and a tap on a chip narrows what came back`() {
        jellyfin.browse.titles += FakeTitle("m1", "Heat", libraryId = "lib")
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.modelReady()
        vm.setText("heat")
        val ready = vm.settled { it.body is SearchBody.Ready }.body as SearchBody.Ready
        assertTrue(ready.hits.any { it.id == "m1" })
        vm.selectType(SearchType.People)
        assertTrue((vm.settled { it.type == SearchType.People }.body as SearchBody.Ready).hits.isEmpty())
        vm.clear()
        assertTrue(vm.settled { it.body is SearchBody.Idle }.text.isEmpty())
    }

    @Test
    fun `what the field held before a process death is searched when the tab starts`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel(initialQuery = "heat")
        assertEquals("heat", vm.settled { it.text == "heat" }.text)
    }

    @Test
    fun `a title's picture URL names the item and its tag at a width, and carries no token`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.modelReady()
        val hit =
            SearchHit.Title(
                BrowseItem(
                    "m1",
                    "Heat",
                    ItemKind.Movie,
                    imageTags =
                        mapOf(
                            com.cooper.homelab.ephyra.browse.ImageKind.Primary to "tag-1",
                        ),
                ),
            )
        val url = vm.imageUrl(hit, 300).orEmpty()
        assertTrue(url, url.contains("/Items/m1/Images/Primary") && url.contains("tag=tag-1") && url.contains("maxWidth=300"))
        assertFalse(url, url.contains("token-1"))
        assertNull(vm.imageUrl(SearchHit.Title(BrowseItem("m2", "No picture", ItemKind.Movie)), 300))
    }

    @Test
    fun `a person's picture URL comes from their tag, and a person with none has none`() {
        runBlocking { sessions.save(jellyfin.session()) }
        val vm = viewModel()
        vm.modelReady()
        val url = vm.imageUrl(SearchHit.Person(PersonResult("p1", "Al", "pt")), 200).orEmpty()
        assertTrue(url, url.contains("/Items/p1/Images/Primary") && url.contains("tag=pt"))
        assertNull(vm.imageUrl(SearchHit.Person(PersonResult("p2", "Bo", null)), 200))
    }

    @Test
    fun `before the client is read there is no picture URL`() {
        val vm = viewModel()
        assertNull(vm.imageUrl(SearchHit.Person(PersonResult("p1", "Al", "pt")), 200))
    }

    @Test
    fun `the actions before the model exists do nothing`() {
        val vm = viewModel()
        vm.setText("heat")
        vm.submit()
        vm.loadMore()
        vm.pickRecent("alien")
        vm.clearRecents()
        vm.clear()
        vm.selectType(SearchType.Movies)
        assertTrue(vm.settled { it.body is SearchBody.Failed }.body is SearchBody.Failed)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
