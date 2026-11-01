package com.cooper.binge.companion.jellyfin.ui.console

import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeLayoutViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val current = JellyfinServerProfile.from("10.11.6")

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        jellyfin.close()
    }

    private fun HomeLayoutViewModel.settled(check: (HomeLayoutUiState) -> Boolean = { it !is HomeLayoutUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun HomeLayoutViewModel.ready() = settled() as HomeLayoutUiState.Ready

    private fun saves(count: Int) {
        runBlocking {
            withTimeout(TIMEOUT_MILLIS) {
                while (jellyfin.hits("/Users/Configuration") < count) delay(POLL_MILLIS)
            }
        }
    }

    private fun ids(key: String) = (jellyfin.lastBody("/Users/Configuration").getValue(key) as JsonArray).map { it.jsonPrimitive.content }

    @Test
    fun `reads the libraries in the saved order, the rest after, and the server's defaults`() {
        jellyfin.userConfiguration = """{"OrderedViews":["lib-shows"],"MyMediaExcludes":["lib-home"]}"""
        val ready = HomeLayoutViewModel(connection, current).ready()
        assertEquals(listOf("lib-shows", "lib-movies", "lib-home"), ready.libraries.map { it.id })
        assertEquals(setOf("lib-home"), ready.layout.myMediaExcludes)
        assertEquals(setOf("lib-home"), ready.groupable)
        assertTrue(ready.layout.hidePlayedInLatest)
        assertFalse(ready.layout.displayMissingEpisodes)
    }

    @Test
    fun `a current server is asked with UserViews and an older one the old way`() {
        HomeLayoutViewModel(connection, current).ready()
        assertEquals(1, jellyfin.hits("/UserViews"))
        HomeLayoutViewModel(connection, JellyfinServerProfile.from("10.8.13")).ready()
        assertEquals(1, jellyfin.hits("/Users/user-1/Views"))
        assertEquals(1, jellyfin.hits("/Users/user-1/GroupingOptions"))
    }

    @Test
    fun `hiding a library from My Media and Latest saves each list, the rest as it was`() {
        val vm = HomeLayoutViewModel(connection, current)
        vm.ready()
        vm.setInMyMedia("lib-movies", false)
        saves(1)
        vm.setInLatest("lib-movies", false)
        saves(2)
        assertEquals(listOf("lib-movies"), ids("MyMediaExcludes"))
        assertEquals(listOf("lib-movies"), ids("LatestItemsExcludes"))
        assertEquals(
            "eng",
            jellyfin
                .lastBody("/Users/Configuration")
                .getValue("AudioLanguagePreference")
                .jsonPrimitive.content,
        )
        vm.setInMyMedia("lib-movies", true)
        saves(3)
        assertEquals(emptyList<String>(), ids("MyMediaExcludes"))
    }

    @Test
    fun `moving a library saves the whole order, and the ends don't move`() {
        val vm = HomeLayoutViewModel(connection, current)
        vm.ready()
        vm.move("lib-home", -1)
        saves(1)
        assertEquals(listOf("lib-movies", "lib-home", "lib-shows"), ids("OrderedViews"))
        assertEquals(listOf("lib-movies", "lib-home", "lib-shows"), vm.ready().libraries.map { it.id })
        vm.move("lib-movies", -1)
        vm.move("lib-shows", 1)
        assertEquals(1, jellyfin.hits("/Users/Configuration"))
    }

    @Test
    fun `grouping and the display switches save as they're made`() {
        val vm = HomeLayoutViewModel(connection, current)
        vm.ready()
        vm.setGrouped("lib-home", true)
        saves(1)
        assertEquals(listOf("lib-home"), ids("GroupedFolders"))
        vm.setDisplayMissingEpisodes(true)
        saves(2)
        vm.setHidePlayedInLatest(false)
        saves(3)
        vm.setDisplayCollectionsView(true)
        saves(4)
        val body = jellyfin.lastBody("/Users/Configuration")
        assertEquals(true, body.getValue("DisplayMissingEpisodes").jsonPrimitive.booleanOrNull)
        assertEquals(false, body.getValue("HidePlayedInLatest").jsonPrimitive.booleanOrNull)
        assertEquals(true, body.getValue("DisplayCollectionsView").jsonPrimitive.booleanOrNull)
    }

    @Test
    fun `a save that fails puts the change back and says so`() {
        val vm = HomeLayoutViewModel(connection, current)
        vm.ready()
        jellyfin.status["/Users/Configuration"] = 500
        vm.move("lib-shows", -1)
        val failed = vm.settled { (it as? HomeLayoutUiState.Ready)?.saveFailed != null } as HomeLayoutUiState.Ready
        assertEquals(ConsoleFailure.Refused, failed.saveFailed)
        assertEquals(listOf("lib-movies", "lib-shows", "lib-home"), failed.libraries.map { it.id })
    }

    @Test
    fun `a library's page opens and closes`() {
        val vm = HomeLayoutViewModel(connection, current)
        vm.ready()
        vm.open("lib-shows")
        assertEquals("Shows", vm.ready().openLibrary?.name)
        vm.open(null)
        assertNull(vm.ready().openLibrary)
    }

    @Test
    fun `grouping that can't be read leaves the rest of the page`() {
        jellyfin.status["/UserViews/GroupingOptions"] = 500
        val ready = HomeLayoutViewModel(connection, current).ready()
        assertEquals(3, ready.libraries.size)
        assertEquals(emptySet<String>(), ready.groupable)
    }

    @Test
    fun `libraries that can't be read fail the page`() {
        jellyfin.status["/UserViews"] = 500
        assertEquals(HomeLayoutUiState.Failed(ConsoleFailure.Refused), HomeLayoutViewModel(connection, current).settled())
    }

    private companion object {
        const val POLL_MILLIS = 10L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
