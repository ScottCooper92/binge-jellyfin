package com.cooper.homelab.ephyra.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.JellyfinServerProfile
import com.cooper.homelab.ephyra.server.JellyfinVersion
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A library's screen closes itself once the server's list is read and no longer holds the library, and not before:
 * a list that failed to load says nothing about the library (#394).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LibraryRouteGoneTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger)
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 0))
    private var backs by mutableIntStateOf(0)

    @Before
    fun signIn() {
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun stop() {
        connection.close()
        jellyfin.close()
    }

    private fun show(id: String) =
        compose.setContent {
            JellyfinTheme { LibraryItemRoute(connection, profile, libraryItem(id), openSubItem = {}, onBack = { backs++ }) }
        }

    /** [check], after the main looper has run: a condition that reads no semantics would otherwise never let the route load. */
    private fun settled(check: () -> Boolean): Boolean {
        compose.waitForIdle()
        return check()
    }

    @Test
    fun `a library the server no longer lists closes its screen once`() {
        show("lib-gone")
        compose.waitUntil(TIMEOUT_MILLIS) { settled { backs > 0 } }
        compose.waitForIdle()
        assertEquals(1, backs)
    }

    @Test
    fun `a library that is listed stays open`() {
        show("lib-movies")
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("Name")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(0, backs)
    }

    @Test
    fun `a list that failed to load does not close it`() {
        jellyfin.status["/Library/VirtualFolders"] = 500
        show("lib-gone")
        compose.waitUntil(TIMEOUT_MILLIS) { settled { jellyfin.requests.any { it.url.encodedPath == "/Library/VirtualFolders" } } }
        compose.waitForIdle()
        Thread.sleep(SETTLE_MILLIS)
        compose.waitForIdle()
        assertEquals(0, backs)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val SETTLE_MILLIS = 500L
    }
}
