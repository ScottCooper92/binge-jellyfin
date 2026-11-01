package com.cooper.binge.companion.jellyfin.ui.console

import androidx.lifecycle.ViewModel
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.ServerFolder
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FolderBrowserViewModelTest {
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

    private fun FolderBrowserViewModel.ready(check: (FolderBrowseState.Ready) -> Boolean = { true }) =
        runBlocking {
            withTimeout(
                TIMEOUT_MILLIS,
            ) { state.first { it is FolderBrowseState.Ready && check(it) } }
        } as FolderBrowseState.Ready

    @Test
    fun `starts at the server's drives, goes into a folder and back up`() {
        val vm = made(FolderBrowserViewModel(connection))
        vm.start()
        val roots = vm.ready()
        assertEquals(listOf(ServerFolder("media", "/media")), roots.folders)
        assertNull(roots.current)
        vm.enter(roots.folders.first())
        val inside = vm.ready { it.current != null }
        assertEquals(listOf(ServerFolder("movies", "/media/movies"), ServerFolder("tv", "/media/tv")), inside.folders)
        assertEquals("/media", inside.current?.path)
        assertEquals(
            "/media",
            jellyfin.requests
                .last { it.url.encodedPath == "/Environment/DirectoryContents" }
                .url
                .queryParameter("path"),
        )
        assertEquals(
            "false",
            jellyfin.requests
                .last { it.url.encodedPath == "/Environment/DirectoryContents" }
                .url
                .queryParameter("includeFiles"),
        )
        vm.up()
        assertNull(vm.ready { it.current == null }.current)
        // Up from the roots stays at the roots.
        vm.up()
        assertNull(vm.ready().current)
    }

    @Test
    fun `a folder that can't be read says why, and Try again reads it again`() {
        val vm = made(FolderBrowserViewModel(connection))
        jellyfin.status["/Environment/Drives"] = 500
        vm.start()
        runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is FolderBrowseState.Failed } } }
        jellyfin.status.remove("/Environment/Drives")
        vm.retry()
        assertEquals(1, vm.ready().folders.size)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
