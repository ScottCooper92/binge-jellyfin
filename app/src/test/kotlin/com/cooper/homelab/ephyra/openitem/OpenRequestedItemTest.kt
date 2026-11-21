package com.cooper.homelab.ephyra.openitem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.ui.UiState
import com.cooper.homelab.ephyra.ui.nav.HomeRoute
import com.cooper.homelab.ephyra.ui.nav.HubRoute
import com.cooper.homelab.ephyra.ui.nav.LibraryRoute
import com.cooper.homelab.ephyra.ui.nav.MovieRoute
import com.cooper.homelab.ephyra.ui.nav.PlayerRoute
import com.cooper.homelab.ephyra.ui.nav.SeriesRoute
import com.cooper.homelab.ephyra.ui.nav.ShellRoute
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The effect that acts on a request another app made, without the title pages (which draw a mesh Robolectric cannot build): when it
 * waits, where a request lands on the back stack, and that one the app no longer wants is dropped.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class OpenRequestedItemTest {
    private val session = JellyfinSession("http://jelly.lan:8096", "Jelly", "sid", "10.11.0", "uid", "user", "token")
    private val signedIn = UiState.SignedIn(session)
    private val request = requireNotNull(OpenItemRequest.parse("http://jelly.lan:8096", "0123456789abcdef0123456789abcdef", "movie", null))
    private val resolved = mutableListOf<OpenItemRequest>()

    private fun stack(vararg keys: NavKey) = NavBackStack<NavKey>(*keys)

    private fun goesTo(target: OpenTarget): suspend (OpenItemRequest, UiState.SignedIn) -> OpenTarget =
        { asked, _ ->
            resolved += asked
            target
        }

    @Test
    fun `a film goes over the shell once the user is signed in and the client is on`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute)
            requests.offer(request)
            setContent { OpenRequestedItem(requests, signedIn, true, back, goesTo(OpenTarget.Title(ItemKind.Movie, "m1"))) }
            waitUntil { back.size == 2 }
            assertEquals(listOf(ShellRoute, MovieRoute("m1")), back.toList())
            assertEquals(listOf(request), resolved)
        }

    @Test
    fun `a series goes over the shell`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute)
            requests.offer(request)
            setContent { OpenRequestedItem(requests, signedIn, true, back, goesTo(OpenTarget.Title(ItemKind.Series, "s1"))) }
            waitUntil { back.size == 2 }
            assertEquals(listOf(ShellRoute, SeriesRoute("s1")), back.toList())
        }

    @Test
    fun `a running app keeps what is open and the film goes over it, even over a player`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute, LibraryRoute("lib"), PlayerRoute("sitting", "A film"))
            requests.offer(request)
            setContent { OpenRequestedItem(requests, signedIn, true, back, goesTo(OpenTarget.Title(ItemKind.Movie, "m1"))) }
            waitUntil { back.size == 4 }
            assertEquals(listOf(ShellRoute, LibraryRoute("lib"), PlayerRoute("sitting", "A film"), MovieRoute("m1")), back.toList())
        }

    @Test
    fun `a request made before the session is read waits for it`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(HomeRoute)
            var state by mutableStateOf<UiState>(UiState.Loading)
            requests.offer(request)
            setContent { OpenRequestedItem(requests, state, true, back, goesTo(OpenTarget.Title(ItemKind.Movie, "m1"))) }
            waitForIdle()
            assertTrue("nothing is read while the session is", resolved.isEmpty())
            state = signedIn
            waitForIdle()
            assertTrue("nor before the root has settled to the shell", resolved.isEmpty())
            back[0] = ShellRoute
            waitForIdle()
            assertEquals(listOf(ShellRoute, MovieRoute("m1")), back.toList())
        }

    @Test
    fun `a request made before the client gate is read waits for it`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute)
            var client by mutableStateOf<Boolean?>(null)
            requests.offer(request)
            setContent { OpenRequestedItem(requests, signedIn, client, back, goesTo(OpenTarget.Title(ItemKind.Movie, "m1"))) }
            waitForIdle()
            assertTrue(resolved.isEmpty())
            client = true
            waitForIdle()
            assertEquals(listOf<NavKey>(ShellRoute, MovieRoute("m1")), back.toList())
        }

    @Test
    fun `with the client off the request is dropped and never resolved`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(HubRoute)
            requests.offer(request)
            setContent { OpenRequestedItem(requests, signedIn, false, back, goesTo(OpenTarget.Title(ItemKind.Movie, "m1"))) }
            waitForIdle()
            assertTrue(resolved.isEmpty())
            assertEquals(listOf<NavKey>(HubRoute), back.toList())
        }

    @Test
    fun `with nobody signed in the request is dropped and does not come back after a sign in`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(HomeRoute)
            var state by mutableStateOf<UiState>(UiState.EnterServer())
            requests.offer(request)
            setContent { OpenRequestedItem(requests, state, true, back, goesTo(OpenTarget.Title(ItemKind.Movie, "m1"))) }
            waitForIdle()
            state = signedIn
            back[0] = ShellRoute
            waitForIdle()
            assertTrue(resolved.isEmpty())
            assertEquals(listOf<NavKey>(ShellRoute), back.toList())
        }

    @Test
    fun `a request that leads nowhere changes nothing`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute)
            requests.offer(request)
            setContent { OpenRequestedItem(requests, signedIn, true, back, goesTo(OpenTarget.Home)) }
            waitUntil { resolved.isNotEmpty() }
            waitForIdle()
            assertEquals(listOf<NavKey>(ShellRoute), back.toList())
        }

    @Test
    fun `a request is not applied if the app stopped being ready while it was resolved`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute)
            var state by mutableStateOf<UiState>(signedIn)
            val release = CompletableDeferred<Unit>()
            requests.offer(request)
            setContent {
                OpenRequestedItem(requests, state, true, back) { asked, _ ->
                    resolved += asked
                    release.await()
                    OpenTarget.Title(ItemKind.Movie, "m1")
                }
            }
            waitUntil { resolved.isNotEmpty() }
            state = UiState.EnterServer()
            waitForIdle()
            release.complete(Unit)
            waitForIdle()
            assertEquals(listOf<NavKey>(ShellRoute), back.toList())
        }

    @Test
    fun `only the last of two quick requests is acted on`() =
        runComposeUiTest {
            val requests = OpenItemRequests()
            val back = stack(ShellRoute)
            val second = requireNotNull(OpenItemRequest.parse(null, null, "tv", 1399))
            requests.offer(request)
            requests.offer(second)
            setContent { OpenRequestedItem(requests, signedIn, true, back, goesTo(OpenTarget.Title(ItemKind.Series, "s1"))) }
            waitUntil { back.size == 2 }
            assertEquals(listOf(second), resolved)
        }
}
