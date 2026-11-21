package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.ephyra.AppGraph
import com.cooper.homelab.ephyra.ConsoleHost
import com.cooper.homelab.ephyra.FakeItem
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.FakeTitle
import com.cooper.homelab.ephyra.PlainCipher
import com.cooper.homelab.ephyra.auth.SessionStore
import com.cooper.homelab.ephyra.auth.forLibrary
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.flags.LocalClientEnabled
import com.cooper.homelab.ephyra.openitem.OpenItemRequest
import com.cooper.homelab.ephyra.openitem.OpenTarget
import com.cooper.homelab.ephyra.openitem.resolveFor
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.server.LibraryIndex
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.nav.HomeRoute
import com.cooper.homelab.ephyra.ui.nav.MovieRoute
import com.cooper.homelab.ephyra.ui.nav.ShellRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A title another app asked for, in the real [ConsoleHost] over a signed-in session and against the fake server: a request that leads
 * nowhere, or arrives with the client off, leaves a normal launch; and the glue that resolves one reads the server as the app does. The
 * title pages themselves draw a mesh Robolectric cannot build, so where a request lands is tested in [OpenRequestedItemTest].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class OpenRequestedItemHostTest {
    private val jellyfin = FakeJellyfin()
    private val sessions: SessionStore = testSessionStore()
    private val teardown = ViewModelTeardown()
    private val connection =
        teardown.made(Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO, DiscardLogger))
    private val graph by lazy { AppGraph(ApplicationProvider.getApplicationContext(), sessionsForTest = sessions) }
    private val movieId = "0123456789abcdef0123456789abcdef"
    private val seriesId = "fedcba9876543210fedcba9876543210"

    @Before
    fun main() = Dispatchers.setMain(MainLooperDispatcher)

    @After
    fun close() =
        teardown.close(jellyfin, idle = {
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
        })

    /** The back stack the composition made, so the test can read where the app is. */
    private var shown: NavBackStack<NavKey>? = null

    @Composable
    private fun App(clientOn: Boolean) {
        val viewModel =
            remember {
                teardown.made(
                    JellyfinViewModel(
                        sessions,
                        connection,
                        LibraryIndex(),
                        dispatcher = Dispatchers.IO,
                        cipher = PlainCipher.forLibrary(),
                        discover = { emptyList() },
                    ),
                )
            }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val backStack = rememberNavBackStack(HomeRoute).also { shown = it }
        val actions = Actions(onAddress = viewModel::onAddress)
        CompositionLocalProvider(LocalClientEnabled provides clientOn) {
            JellyfinTheme { ConsoleHost(state, backStack, graph, actions, actions, clientResolved = clientOn) }
        }
    }

    private fun request(
        itemId: String,
        type: String,
    ) = requireNotNull(OpenItemRequest.parse(jellyfin.baseUrl, itemId, type, tmdbId = null))

    private fun signedIn() = UiState.SignedIn(jellyfin.session())

    @Test
    fun `the glue finds a film by its id on the signed in server`() {
        jellyfin.browse.titles.add(FakeTitle(movieId, "Alien", "lib-movies", "Movie"))
        runBlocking { sessions.save(jellyfin.session()) }
        val target = runBlocking { graph.resolveFor(request(movieId, "movie"), signedIn()) }
        assertEquals(OpenTarget.Title(ItemKind.Movie, movieId), target)
    }

    @Test
    fun `the glue finds a series by its id on the signed in server`() {
        jellyfin.browse.titles.add(FakeTitle(seriesId, "Severance", "lib-series", "Series"))
        runBlocking { sessions.save(jellyfin.session()) }
        val target = runBlocking { graph.resolveFor(request(seriesId, "tv"), signedIn()) }
        assertEquals(OpenTarget.Title(ItemKind.Series, seriesId), target)
    }

    @Test
    fun `the glue falls to the TMDB id when the server has no such id`() {
        jellyfin.items.add(FakeItem(movieId, "Alien", "Movie", tmdbId = 603))
        runBlocking { sessions.save(jellyfin.session()) }
        val asked = requireNotNull(OpenItemRequest.parse(jellyfin.baseUrl, seriesId, "movie", tmdbId = 603))
        assertEquals(OpenTarget.Title(ItemKind.Movie, movieId), runBlocking { graph.resolveFor(asked, signedIn()) })
    }

    @Test
    fun `the glue opens the home with no session`() {
        val target = runBlocking { graph.resolveFor(request(movieId, "movie"), signedIn()) }
        assertEquals(OpenTarget.Home, target)
    }

    @Test
    fun `an item the server does not have leaves a normal launch`() =
        runAndroidComposeUiTest<ComponentActivity> {
            runBlocking { sessions.save(jellyfin.session()) }
            graph.openRequests.offer(request(movieId, "movie"))
            setContent { App(clientOn = true) }
            waitUntil(timeoutMillis = WAIT_MILLIS) {
                waitForIdle()
                shown?.firstOrNull() == ShellRoute
            }
            repeat(SETTLE_PASSES) { waitForIdle() }
            assertEquals(listOf<NavKey>(ShellRoute), shown?.toList())
        }

    @Test
    fun `with the client off the request is dropped and the app opens as a normal launch`() =
        runAndroidComposeUiTest<ComponentActivity> {
            jellyfin.browse.titles.add(FakeTitle(movieId, "Alien", "lib-movies", "Movie"))
            runBlocking { sessions.save(jellyfin.session()) }
            graph.openRequests.offer(request(movieId, "movie"))
            setContent { App(clientOn = false) }
            waitUntil(timeoutMillis = WAIT_MILLIS) {
                waitForIdle()
                shown?.firstOrNull() != HomeRoute && shown?.isNotEmpty() == true
            }
            repeat(SETTLE_PASSES) { waitForIdle() }
            assertTrue("no title page opened: ${shown?.toList()}", shown?.none { it is MovieRoute } == true)
            assertEquals(1, shown?.size)
        }

    private companion object {
        const val WAIT_MILLIS = 10_000L
        const val SETTLE_PASSES = 20
    }
}
