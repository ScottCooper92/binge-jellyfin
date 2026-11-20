package com.cooper.homelab.ephyra.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.core.presentation.connect.LinkStash
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.ConsoleHost
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.PlainCipher
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.auth.forLibrary
import com.cooper.homelab.ephyra.flags.LocalClientEnabled
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.nav.HomeRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A process death, as the device does it (#823): the saved UI state comes back, and the view model does not. `recreate()` on the
 * activity is a configuration change, which keeps the view models and so never shows what a killed process shows: a fresh
 * [JellyfinViewModel] at `Loading` that reads the session again while the restored back stack is already on screen. This composes the
 * real [ConsoleHost] over a real view model, and rebuilds the composition from its saved state with a new view model.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class ProcessDeathRestoreTest {
    private val jellyfin = FakeJellyfin()
    private val sessions: SessionStore = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))
    private val graph by lazy { AppGraph(ApplicationProvider.getApplicationContext(), sessionsForTest = sessions) }

    /**
     * Main is confined to the main thread, as on a device (#898, #904), so a collector in the composition resumes there whichever thread
     * emits. See [MainLooperDispatcher] for what that asks of a test: every wait here either evaluates a node or calls `waitForIdle()`,
     * and none blocks the main thread on work that runs on it.
     */
    @Before
    fun main() = Dispatchers.setMain(MainLooperDispatcher)

    @After
    fun close() = teardown.close(jellyfin, idle = { shadowOf(Looper.getMainLooper()).idle() })

    /** The view model the composition last built, so a test can read what it restored. */
    private var shown: JellyfinViewModel? = null

    /** What a process that died left for the next: a stash that outlives the view models, as the file does. */
    private class MemoryStash : LinkStash {
        @Volatile
        var value: String? = null

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
        }

        override fun clear() {
            value = null
        }
    }

    /** The app's screen over a view model that is new each time the composition is built from nothing but its saved state. */
    @Composable
    private fun App(linkStash: LinkStash = NoLinkStash) {
        val viewModel =
            remember {
                teardown
                    .made(
                        JellyfinViewModel(
                            sessions,
                            connection,
                            LibraryIndex(),
                            dispatcher = Dispatchers.IO,
                            cipher = PlainCipher.forLibrary(),
                            discover = { emptyList() },
                            linkStash = linkStash,
                        ),
                    ).also { shown = it }
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val backStack = rememberNavBackStack(HomeRoute)
        val actions = Actions(onAddress = viewModel::onAddress)
        CompositionLocalProvider(LocalClientEnabled provides true) {
            JellyfinTheme {
                ConsoleHost(state, backStack, graph, actions, actions, clientResolved = true)
            }
        }
    }

    @Test
    fun `a signed in app killed with the client on comes back to its shell`() =
        runAndroidComposeUiTest<ComponentActivity> {
            runBlocking { sessions.save(jellyfin.session()) }
            val tester = StateRestorationTester(this)
            tester.setContent { App() }
            waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag("tab_server").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab_server").performClick()
            waitForIdle()

            tester.emulateSaveAndRestore()

            waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag("tab_server").fetchSemanticsNodes().isNotEmpty() }
            assertTrue("the shell is on screen after the restore", onAllNodesWithTag("tab_server").fetchSemanticsNodes().isNotEmpty())
        }

    @Test
    @Config(qualifiers = "w411dp-h891dp-xhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a library open with its order and filters comes back with them`() =
        runAndroidComposeUiTest<ComponentActivity> {
            runBlocking { sessions.save(jellyfin.session()) }
            val tester = StateRestorationTester(this)
            tester.setContent { App() }
            waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag("tab_libraries").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("tab_libraries").performClick()
            waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithContentDescription("Movies").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Movies").performClick()
            waitUntil(
                timeoutMillis = WAIT_MILLIS,
            ) { onAllNodesWithContentDescription("Filters and order").fetchSemanticsNodes().isNotEmpty() }
            // The first page is in before the pill is used: a tap on it while the grid is still a skeleton did not open the sheet.
            waitUntil(
                timeoutMillis = WAIT_MILLIS,
            ) { onAllNodesWithText("This library has nothing in it yet.").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("Filters and order").performClick()
            waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithText("Date added").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Date added").performScrollTo().performClick()
            onNodeWithText("Show results").performClick()
            waitUntil(timeoutMillis = WAIT_MILLIS) {
                waitForIdle()
                readsByDateAdded() > 0
            }
            val before = readsByDateAdded()
            tester.emulateSaveAndRestore()

            // The library is on screen again, and its first page is asked for in the order the user chose, not the default.
            waitUntil(timeoutMillis = WAIT_MILLIS) {
                waitForIdle()
                readsByDateAdded() > before
            }
            assertTrue(
                "the library's grid is on screen after the restore",
                onAllNodesWithContentDescription("Filters and order").fetchSemanticsNodes().isNotEmpty(),
            )
        }

    /** How many times the server has been asked for a library's titles newest first. */
    private fun readsByDateAdded(): Int =
        jellyfin.requests.count { it.url.encodedPath == "/Items" && it.url.queryParameter("sortBy")?.startsWith("DateCreated") == true }

    @Test
    fun `a Quick Connect wait the killed process left comes back with its code, and approving it signs in to the shell`() =
        runAndroidComposeUiTest<ComponentActivity> {
            val stash = MemoryStash()
            // The process that dies: it shows a code, keeps the wait, and is gone before anything is approved.
            val dying =
                teardown.made(
                    JellyfinViewModel(
                        sessions,
                        connection,
                        LibraryIndex(),
                        dispatcher = Dispatchers.IO,
                        cipher = PlainCipher.forLibrary(),
                        discover = { emptyList() },
                        linkStash = stash,
                    ),
                )
            awaitState(dying) { it is UiState.EnterServer }
            dying.onAddress(jellyfin.baseUrl)
            dying.connect()
            awaitState(dying) { it is UiState.SignIn }
            dying.startQuickConnect()
            val code =
                requireNotNull(
                    (awaitState(dying) { it is UiState.SignIn && it.quickConnectCode != null } as UiState.SignIn).quickConnectCode,
                )
            val dyingJob = requireNotNull(dying.viewModelScope.coroutineContext[Job])
            dyingJob.cancel()
            // Not a blocking join: the cancelled work finishes on the main thread, which a block would hold.
            waitUntil(timeoutMillis = WAIT_MILLIS) {
                waitForIdle()
                dyingJob.isCompleted
            }
            assertNotNull("the wait outlives the process", stash.value)

            // The process that comes back: a new view model reads the wait, and the screen it composes shows that code again.
            val tester = StateRestorationTester(this)
            tester.setContent { App(stash) }
            val restored = awaitState(requireNotNull(shown)) { it is UiState.SignIn && it.quickConnectCode != null } as UiState.SignIn
            assertEquals("the user sees the code they were given", code, restored.quickConnectCode)

            // The user approves it from the other Jellyfin app, and this one signs in to its shell.
            jellyfin.quickConnectApproved = true
            waitUntil(timeoutMillis = WAIT_MILLIS) { onAllNodesWithTag("tab_server").fetchSemanticsNodes().isNotEmpty() }
            assertNull("nothing is kept once it has signed in", stash.value)
        }

    /** Waits with the test's own idling, which runs the main looper where Main's work is, and does not block the main thread. */
    private fun AndroidComposeUiTest<ComponentActivity>.awaitState(
        viewModel: JellyfinViewModel,
        matches: (UiState) -> Boolean,
    ): UiState {
        waitUntil(timeoutMillis = WAIT_MILLIS) {
            waitForIdle()
            matches(viewModel.state.value)
        }
        return viewModel.state.value
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
