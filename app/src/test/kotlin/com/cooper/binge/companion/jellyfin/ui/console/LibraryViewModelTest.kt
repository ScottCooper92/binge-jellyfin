package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LIBRARY_ENABLED
import com.cooper.binge.companion.jellyfin.server.LIBRARY_REALTIME
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()
    private val movies = ServerLibrary("lib-movies", "Movies", "movies", listOf("/media/movies"))

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.libraries =
            Json
                .parseToJsonElement(
                    """[{"Name":"Movies","CollectionType":"movies","ItemId":"lib-movies","Locations":["/media/movies"],""" +
                        """"LibraryOptions":{"Enabled":true,"EnableRealtimeMonitor":false,"FutureOption":{"keep":"me"},""" +
                        """"SubtitleFetcherOrder":["Podnapisi"],"DisabledSubtitleFetchers":[]}},""" +
                        """{"Name":"Shows","CollectionType":"tvshows","ItemId":"lib-shows","Locations":["/media/tv"]}]""",
                ).jsonArray
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun LibraryViewModel.ready(check: (LibraryUiState.Ready) -> Boolean = { true }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first { it is LibraryUiState.Ready && check(it) } } } as LibraryUiState.Ready

    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(TIMEOUT_MILLIS) { while (!condition()) delay(POLL_MILLIS) } }

    private fun saves(count: Int) = waitFor { jellyfin.requests.count { it.url.encodedPath == OPTIONS && it.method == "POST" } >= count }

    private fun savedOptions(): JsonObject = jellyfin.lastBody(OPTIONS).getValue("LibraryOptions").jsonObject

    @Test
    fun `reads the library with its options and what the server offers`() {
        val ready = made(LibraryViewModel(connection, movies)).ready()
        assertEquals("Movies", ready.library.name)
        assertEquals(
            false,
            ready.options
                .getValue("EnableRealtimeMonitor")
                .jsonPrimitive.boolean,
        )
        assertEquals(listOf("Open Subtitles", "Podnapisi"), ready.available.subtitleFetchers)
        assertEquals(listOf("LrcLib"), ready.available.lyricFetchers)
        assertEquals(listOf("Nfo"), ready.available.metadataReaders)
        assertEquals(listOf("Chapter Segments"), ready.available.segmentProviders)
        // The server's language and country lists are read for the pickers.
        assertTrue(ready.cultures.isNotEmpty())
        assertEquals(
            "movies",
            jellyfin.requests
                .last {
                    it.url.encodedPath == "/Libraries/AvailableOptions"
                }.url
                .queryParameter("libraryContentType"),
        )
    }

    @Test
    fun `an option saves as it is made, with the whole object and an option this app doesn't know untouched`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.set(LIBRARY_REALTIME, JsonPrimitive(true))
        assertEquals(
            true,
            vm
                .ready {
                    it.options[LIBRARY_REALTIME] == JsonPrimitive(true)
                }.options
                .getValue(LIBRARY_REALTIME)
                .jsonPrimitive.boolean,
        )
        saves(1)
        assertEquals(
            "lib-movies",
            jellyfin
                .lastBody(OPTIONS)
                .getValue("Id")
                .jsonPrimitive.content,
        )
        val sent = savedOptions()
        assertEquals(true, sent.getValue(LIBRARY_REALTIME).jsonPrimitive.boolean)
        assertEquals(true, sent.getValue(LIBRARY_ENABLED).jsonPrimitive.boolean)
        assertEquals(
            "me",
            sent
                .getValue("FutureOption")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a refused save puts the option back and says so`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        jellyfin.status["/Library/VirtualFolders/LibraryOptions"] = 500
        vm.set(LIBRARY_REALTIME, JsonPrimitive(true))
        val failed = vm.ready { it.saveFailed != null }
        assertEquals(JsonPrimitive(false), failed.options[LIBRARY_REALTIME])
        assertEquals(ConsoleFailure.Refused, failed.saveFailed)
    }

    @Test
    fun `switching a provider off and moving it save the disabled list and the whole order`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.setProvider(ProviderGroup.Subtitles, "Podnapisi", enabled = false)
        saves(1)
        assertEquals(listOf("Podnapisi"), savedOptions().getValue("DisabledSubtitleFetchers").jsonArray.map { it.jsonPrimitive.content })
        vm.moveProvider(ProviderGroup.Subtitles, "Podnapisi", 1)
        saves(2)
        assertEquals(
            listOf("Open Subtitles", "Podnapisi"),
            savedOptions().getValue("SubtitleFetcherOrder").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `pages and dialogs open and close`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.show(LibraryPage.Providers(ProviderGroup.Subtitles))
        assertEquals(LibraryPage.Providers(ProviderGroup.Subtitles), vm.ready().page)
        vm.ask(LibraryDialog.Remove)
        assertEquals(LibraryDialog.Remove, vm.ready().dialog)
        vm.ask(null)
        assertNull(vm.ready().dialog)
    }

    @Test
    fun `adding a folder checks it on the server, then adds it and shows the library as the server has it`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.ask(LibraryDialog.AddFolder)
        vm.addFolder("/media/missing")
        assertTrue(vm.ready { it.folderMissing }.dialog == LibraryDialog.AddFolder)
        assertEquals(0, jellyfin.hits("/Library/VirtualFolders/Paths"))
        vm.addFolder("  /media/tv  ")
        val added = vm.ready { it.library.folders.size == 2 }
        assertEquals(listOf("/media/movies", "/media/tv"), added.library.folders)
        assertNull(added.dialog)
        assertFalse(added.busy)
        assertEquals(
            "Movies",
            jellyfin
                .lastBody("/Library/VirtualFolders/Paths")
                .getValue("Name")
                .jsonPrimitive.content,
        )
        assertEquals(
            "/media/tv",
            jellyfin
                .lastBody("/Library/VirtualFolders/Paths")
                .getValue("PathInfo")
                .jsonObject
                .getValue("Path")
                .jsonPrimitive.content,
        )
        vm.addFolder("   ")
        assertEquals(1, jellyfin.hits("/Library/VirtualFolders/Paths"))
    }

    @Test
    fun `removing a folder asks first and then removes just that folder`() {
        val vm = made(LibraryViewModel(connection, movies.copy(folders = listOf("/media/movies"))))
        vm.ready()
        vm.addFolder("/media/tv")
        vm.ready { it.library.folders.size == 2 }
        vm.ask(LibraryDialog.RemoveFolder("/media/tv"))
        vm.removeFolder("/media/tv")
        val removed = vm.ready { it.library.folders.size == 1 }
        assertEquals(listOf("/media/movies"), removed.library.folders)
        assertNull(removed.dialog)
        val request = jellyfin.requests.last { it.url.encodedPath == "/Library/VirtualFolders/Paths" && it.method == "DELETE" }
        assertEquals("Movies", request.url.queryParameter("name"))
        assertEquals("/media/tv", request.url.queryParameter("path"))
        assertEquals("false", request.url.queryParameter("refreshLibrary"))
    }

    @Test
    fun `a refused folder change says so and keeps the library as it was`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        jellyfin.status["/Library/VirtualFolders/Paths"] = 500
        vm.addFolder("/media/tv")
        val failed = vm.ready { it.failed != null }
        assertEquals(ConsoleFailure.Refused, failed.failed)
        assertFalse(failed.busy)
        assertEquals(listOf("/media/movies"), failed.library.folders)
    }

    @Test
    fun `a rename the server has not given an id for yet shows the new name, not a refusal`() {
        jellyfin.withholdIds = true
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.ask(LibraryDialog.Rename)
        vm.rename("Films")
        val renamed = vm.ready { it.library.name == "Films" }
        assertNull(renamed.failed)
        assertEquals("", renamed.library.id)
    }

    @Test
    fun `a library opened with no id takes up the id the server has by now, and saves go to it`() {
        val vm = made(LibraryViewModel(connection, movies.copy(id = "")))
        val ready = vm.ready()
        assertEquals("lib-movies", ready.library.id)
        vm.set(LIBRARY_REALTIME, JsonPrimitive(true))
        saves(1)
        assertEquals(
            "lib-movies",
            jellyfin
                .lastBody(OPTIONS)
                .getValue("Id")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `renaming moves to the library's new id, and saves go to it`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.ask(LibraryDialog.Rename)
        vm.rename("  Films  ")
        val renamed = vm.ready { it.library.name == "Films" }
        assertNull(renamed.dialog)
        assertEquals("lib-films", renamed.library.id)
        val request = jellyfin.requests.last { it.url.encodedPath == "/Library/VirtualFolders/Name" }
        assertEquals("Movies", request.url.queryParameter("name"))
        assertEquals("Films", request.url.queryParameter("newName"))
        vm.set(LIBRARY_REALTIME, JsonPrimitive(true))
        saves(1)
        assertEquals(
            "lib-films",
            jellyfin
                .lastBody(OPTIONS)
                .getValue("Id")
                .jsonPrimitive.content,
        )
        // The options the server holds came through the rename.
        assertEquals(
            "me",
            savedOptions()
                .getValue("FutureOption")
                .jsonObject
                .getValue("keep")
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `a name another library has, the library's own and a blank one are not sent`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.ask(LibraryDialog.Rename)
        vm.rename("shows")
        assertTrue(vm.ready { it.nameTaken }.dialog == LibraryDialog.Rename)
        vm.rename("Movies")
        vm.rename("   ")
        assertEquals(0, jellyfin.hits("/Library/VirtualFolders/Name"))
        vm.ask(null)
        assertFalse(vm.ready().nameTaken)
    }

    @Test
    fun `removing the library asks first, removes only it, and closes the page`() {
        val vm = made(LibraryViewModel(connection, movies))
        vm.ready()
        vm.ask(LibraryDialog.Remove)
        vm.remove()
        val gone = vm.ready { it.gone }
        assertNull(gone.dialog)
        val request = jellyfin.requests.last { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "DELETE" }
        assertEquals("Movies", request.url.queryParameter("name"))
        assertEquals(
            listOf("Shows"),
            jellyfin.libraries.map {
                it.jsonObject
                    .getValue("Name")
                    .jsonPrimitive.content
            },
        )
    }

    @Test
    fun `a library that can't be read fails the page, and the page works without what the server offers`() {
        jellyfin.status["/Libraries/AvailableOptions"] = 500
        val ready = made(LibraryViewModel(connection, movies)).ready()
        assertEquals(emptyList<String>(), ready.available.subtitleFetchers)
        jellyfin.status["/Library/VirtualFolders"] = 500
        val failed = made(LibraryViewModel(connection, movies))
        runBlocking { withTimeout(TIMEOUT_MILLIS) { failed.state.first { it is LibraryUiState.Failed } } }
        assertNotNull(failed.state.value)
    }

    @Test
    fun `a library the server no longer has is shown as it was opened`() {
        jellyfin.libraries = JsonArray(emptyList())
        assertEquals("Movies", made(LibraryViewModel(connection, movies)).ready().library.name)
    }

    private companion object {
        const val OPTIONS = "/Library/VirtualFolders/LibraryOptions"
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 20L
    }
}
