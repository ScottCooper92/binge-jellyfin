package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.LIBRARY_SCAN_TASK
import com.cooper.binge.companion.jellyfin.server.MIXED_LIBRARY
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
import kotlinx.serialization.json.jsonArray
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
class LibrariesViewModelTest {
    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)

    private val teardown = ViewModelTeardown()

    private fun <T : ViewModel> made(viewModel: T): T = teardown.made(viewModel)

    @Before
    fun main() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun close() = teardown.close(jellyfin)

    private fun LibrariesViewModel.settled(check: (LibrariesUiState) -> Boolean = { it !is LibrariesUiState.Loading }) =
        runBlocking { withTimeout(TIMEOUT_MILLIS) { state.first(check) } }

    private fun LibrariesViewModel.ready(check: (LibrariesUiState.Ready) -> Boolean = { true }) =
        settled { it is LibrariesUiState.Ready && check(it) } as LibrariesUiState.Ready

    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(TIMEOUT_MILLIS) { while (!condition()) delay(POLL_MILLIS) } }

    private fun subscribed() = waitFor { jellyfin.socketMessages.any { "ScheduledTasksInfoStart" in it } }

    @Test
    fun `reads the libraries and the scan task`() {
        val ready = made(LibrariesViewModel(connection)).ready()
        assertEquals(
            listOf(
                ServerLibrary("lib-movies", "Movies", "movies", listOf("/media/movies")),
                ServerLibrary("lib-shows", "Shows", "tvshows", listOf("/media/tv", "/media/anime")),
            ),
            ready.libraries,
        )
        assertEquals("t-scan", ready.board.task(LIBRARY_SCAN_TASK)?.id)
        // Hidden tasks are asked to be left out, as the dashboard does.
        assertEquals(
            "false",
            jellyfin.requests
                .last { it.url.encodedPath == "/ScheduledTasks" }
                .url
                .queryParameter("isHidden"),
        )
    }

    @Test
    fun `a scan shows from the tap, reads the server's first 0 as its start and the next as its end`() {
        val vm = made(LibrariesViewModel(connection)).apply { outcomeMillis = OUTCOME_MILLIS }
        val movies = vm.ready().libraries.first()
        subscribed()
        vm.scan(movies)
        assertEquals(LibraryScan(), vm.ready().scanning["lib-movies"])
        waitFor { jellyfin.hits("/Items/lib-movies/Refresh") == 1 }
        assertEquals(
            "true",
            jellyfin.requests
                .last { it.url.encodedPath == "/Items/lib-movies/Refresh" }
                .url
                .queryParameter("Recursive"),
        )
        jellyfin.push(progress("lib-movies", "0"))
        assertEquals(LibraryScan(0, started = true), vm.ready { it.scanning["lib-movies"]?.started == true }.scanning["lib-movies"])
        jellyfin.push(progress("lib-movies", "42.5"))
        vm.ready { it.scanning["lib-movies"]?.percent == 42 }
        jellyfin.push(progress("lib-movies", "0"))
        assertEquals(JobOutcome.Succeeded, vm.ready { it.scanning.isEmpty() }.outcomes["lib-movies"])
        // The outcome shows for a moment, then the row goes back to Scan.
        vm.ready { it.outcomes.isEmpty() }
    }

    @Test
    fun `a scan the server started shows from its first step, and 100 ends it`() {
        val vm = made(LibrariesViewModel(connection)).apply { outcomeMillis = OUTCOME_MILLIS }
        vm.ready()
        subscribed()
        jellyfin.push(progress("lib-shows", "10"))
        assertEquals(10, vm.ready { "lib-shows" in it.scanning }.scanning["lib-shows"]?.percent)
        jellyfin.push(progress("lib-shows", "100"))
        vm.ready { it.scanning.isEmpty() }
        // The root of every library reports too; it isn't a row.
        jellyfin.push(progress("root", "50"))
        jellyfin.push(progress("lib-movies", "0"))
        assertEquals(emptyMap<String, LibraryScan>(), vm.ready().scanning)
    }

    @Test
    fun `scanning everything runs the scan task, follows it, and can stop it`() {
        val vm = made(LibrariesViewModel(connection)).apply { outcomeMillis = OUTCOME_MILLIS }
        vm.ready()
        subscribed()
        vm.scanEverything()
        waitFor { jellyfin.requests.any { it.url.encodedPath == "/ScheduledTasks/Running/t-scan" && it.method == "POST" } }
        val started = vm.ready { "t-scan" in it.board.starting }
        assertTrue(started.board.running(started.board.task(LIBRARY_SCAN_TASK)!!))
        jellyfin.push(tasks("Running", 68.6, "Completed", "2026-10-31T18:00:00Z"))
        assertEquals(
            68,
            vm
                .ready { it.board.task(LIBRARY_SCAN_TASK)?.percent == 68 }
                .board
                .task(LIBRARY_SCAN_TASK)
                ?.percent,
        )
        vm.stopEverything()
        waitFor { jellyfin.requests.any { it.url.encodedPath == "/ScheduledTasks/Running/t-scan" && it.method == "DELETE" } }
        jellyfin.push(tasks("Idle", null, "Completed", "2026-10-31T20:00:00Z"))
        assertEquals(JobOutcome.Succeeded, vm.ready { "t-scan" in it.board.outcomes }.board.outcomes["t-scan"])
    }

    @Test
    fun `a scan that can't start goes back, says so, and shows it failed`() {
        val vm = made(LibrariesViewModel(connection)).apply { outcomeMillis = OUTCOME_MILLIS }
        val movies = vm.ready().libraries.first()
        jellyfin.status["/Items/lib-movies/Refresh"] = 500
        vm.scan(movies)
        val failed = vm.ready { it.actionFailed != null }
        assertEquals(ConsoleFailure.Refused, failed.actionFailed)
        assertEquals(emptyMap<String, LibraryScan>(), failed.scanning)
        assertEquals(JobOutcome.Failed, failed.outcomes["lib-movies"])
        jellyfin.status["/ScheduledTasks/Running/t-scan"] = 500
        vm.scanEverything()
        assertEquals(emptySet<String>(), vm.ready { it.board.busy.isEmpty() && it.actionFailed != null }.board.busy)
    }

    @Test
    fun `a stop that can't be sent is reported as a stop`() {
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        jellyfin.status["/ScheduledTasks/Running/t-scan"] = 500
        vm.stopEverything()
        val failed = vm.ready { it.actionFailed != null }
        assertTrue(failed.stopFailed)
        vm.scanEverything()
        assertEquals(false, vm.ready { it.board.busy.isEmpty() && !it.stopFailed }.stopFailed)
    }

    @Test
    fun `a page that can't read says why, and Try again reads again`() {
        jellyfin.status["/Library/VirtualFolders"] = 403
        val vm = made(LibrariesViewModel(connection))
        assertEquals(LibrariesUiState.Failed(ConsoleFailure.Refused), vm.settled())
        jellyfin.status.remove("/Library/VirtualFolders")
        vm.load()
        assertEquals(2, vm.ready().libraries.size)
    }

    @Test
    fun `a library opens and closes, and the list is read again quietly`() {
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        vm.open("lib-shows")
        assertEquals("lib-shows", vm.ready().opened)
        jellyfin.libraries =
            Json
                .parseToJsonElement(FakeJellyfin.LIBRARIES)
                .jsonArray
                .take(1)
                .let(::JsonArray)
        vm.refresh()
        // The open library went with the refresh, and the list shows what the server has now.
        val refreshed = vm.ready { it.libraries.size == 1 }
        assertNull(refreshed.opened)
        vm.open(null)
        assertNull(vm.ready().opened)
    }

    @Test
    fun `a library the server has not given an id yet still shows, and its name counts as taken`() {
        jellyfin.serverFolders = jellyfin.serverFolders + ("" to listOf("/media", "/media/anime"))
        jellyfin.withholdIds = true
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        vm.ask(true)
        vm.add("Anime", "tvshows", "/media/anime")
        val added = vm.ready { it.libraries.size == 3 && !it.adding }
        assertEquals("", added.libraries.last().id)
        val posts = jellyfin.requests.count { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "POST" }
        vm.add("anime", "tvshows", "/media/anime")
        assertEquals(posts, jellyfin.requests.count { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "POST" })
    }

    @Test
    fun `a new library is added, and shown with the others`() {
        jellyfin.serverFolders = jellyfin.serverFolders + ("" to listOf("/media", "/media/anime"))
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        vm.ask(true)
        assertTrue(vm.ready().adding)
        jellyfin.serverFolders = jellyfin.serverFolders + ("/media/more" to listOf("/media/anime"))
        vm.add("  Anime ", "tvshows", "/media/anime")
        val added = vm.ready { it.libraries.size == 3 }
        assertFalse(added.adding)
        assertFalse(added.saving)
        assertEquals("Anime", added.libraries.last().name)
        val request = jellyfin.requests.last { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "POST" }
        assertEquals("Anime", request.url.queryParameter("name"))
        assertEquals("tvshows", request.url.queryParameter("collectionType"))
        assertEquals("/media/anime", request.url.queryParameter("paths"))
        assertEquals("true", request.url.queryParameter("refreshLibrary"))
    }

    @Test
    fun `mixed content has no collection type, and a path with a comma is added after the library`() {
        jellyfin.serverFolders = jellyfin.serverFolders + ("" to listOf("/media/a,b"))
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        jellyfin.serverFolders = jellyfin.serverFolders + ("/media/more" to listOf("/media/a,b"))
        vm.add("Everything", MIXED_LIBRARY, "/media/a,b")
        val added = vm.ready { it.libraries.size == 3 }
        val request = jellyfin.requests.last { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "POST" }
        assertNull(request.url.queryParameter("collectionType"))
        assertNull(request.url.queryParameter("paths"))
        assertEquals(listOf("/media/a,b"), added.libraries.last().folders)
    }

    @Test
    fun `a name another library has, or a blank name or path, adds nothing`() {
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        vm.add("movies", "movies", "/media/movies")
        vm.add("   ", "movies", "/media/x")
        vm.add("Other", "movies", "  ")
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "POST" })
    }

    @Test
    fun `a folder the server hasn't got makes no library, with or without a comma, and says so`() {
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        vm.ask(true)
        vm.add("Anime", "tvshows", "/media/missing,folder")
        assertTrue(vm.ready { it.folderMissing }.adding)
        vm.add("Anime", "tvshows", "/media/missing")
        assertTrue(vm.ready { it.folderMissing && !it.saving }.folderMissing)
        assertEquals(0, jellyfin.requests.count { it.url.encodedPath == "/Library/VirtualFolders" && it.method == "POST" })
        assertEquals(2, jellyfin.libraries.size)
        vm.ask(true)
        assertFalse(vm.ready().folderMissing)
    }

    @Test
    fun `an add the server refuses says so and keeps the sheet open`() {
        jellyfin.serverFolders = jellyfin.serverFolders + ("" to listOf("/media/anime"))
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        vm.ask(true)
        jellyfin.serverFolders = jellyfin.serverFolders + ("/media/more" to listOf("/media/anime"))
        jellyfin.status["/Library/VirtualFolders"] = 500
        vm.add("Anime", "tvshows", "/media/anime")
        // The status override answers the list too, so the failure is the add's own.
        val failed = vm.ready { it.addFailed != null }
        assertTrue(failed.adding)
        assertFalse(failed.saving)
        vm.ask(false)
        assertNull(vm.ready().addFailed)
    }

    @Test
    fun `an add that fails after the server made the library reads the libraries again`() {
        val vm = made(LibrariesViewModel(connection))
        vm.ready()
        jellyfin.serverFolders = jellyfin.serverFolders + ("/media/more" to listOf("/media/a,b"))
        jellyfin.status["/Library/VirtualFolders/Paths"] = 500
        vm.add("Everything", MIXED_LIBRARY, "/media/a,b")
        val failed = vm.ready { it.addFailed != null && it.libraries.size == 3 }
        assertTrue(failed.libraries.any { it.name == "Everything" })
    }

    private fun progress(
        id: String,
        percent: String,
    ) = """{"MessageType":"RefreshProgress","Data":{"ItemId":"$id","Progress":"$percent"}}"""

    private fun tasks(
        state: String,
        percent: Double?,
        lastStatus: String,
        lastEnded: String,
    ): String {
        val progress = percent?.let { ""","CurrentProgressPercentage":$it""" }.orEmpty()
        val last = ""","LastExecutionResult":{"Status":"$lastStatus","EndTimeUtc":"$lastEnded"}"""
        return """{"MessageType":"ScheduledTasksInfo","Data":[{"Id":"t-clean","Key":"CleanCache","State":"Running"},""" +
            """{"Id":"t-scan","Key":"RefreshLibrary","State":"$state"$progress$last}]}"""
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 10L
        const val OUTCOME_MILLIS = 300L
    }
}
