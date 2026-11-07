package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.NowPlaying
import com.cooper.binge.companion.jellyfin.server.PlayMethod
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.server.ServerTask
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.server.TypeFetchers
import com.cooper.binge.companion.jellyfin.server.UserView
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An item screen is an entry of its own on the stack, so it closes itself when its item leaves the list (a stream that
 * ended, a user who was removed). It stays while the list is loading again or failed to load, since those say nothing
 * about the item. One test per kind of screen, each over the same three states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ItemScreenGoneTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var backs = 0

    private val failure = ConsoleFailure.Unreachable

    private val task = ServerTask("t1", "ScanLibrary", "Scan Media Library", "Scans the library.", "Library", false, false, null, null)
    private val user = ServerUser("u1", "Alice", false, null, JsonObject(emptyMap()))
    private val plugin = ServerPlugin("p1", "AudioDB", "10.11.6.0", "Looks things up.", PluginStatus.Active, false, false)
    private val pkg = CatalogPackage("k1", "AudioDB", "Looks things up.", "", "Jellyfin", "Metadata", emptyList())
    private val view = UserView("v1", "Movies")
    private val fetchers = listOf(TypeFetchers("Movie", metadata = listOf("TheMovieDb")))
    private val session =
        NowPlaying("s1", "alice", "Pixel", "Jellyfin Android", "Big Buck Bunny", null, 0L, null, false, PlayMethod.DirectPlay, emptyList())

    /** Shows [screen] over a state the test can change, and checks it asks to close only when the item is gone. */
    private fun <S> check(
        holds: S,
        without: S,
        loading: S,
        failed: S,
        screen: @Composable (S, () -> Unit) -> Unit,
    ) {
        var state by mutableStateOf(holds)
        compose.setContent { JellyfinTheme { screen(state) { backs++ } } }
        compose.waitForIdle()
        assertEquals("open while its item is in the list", 0, backs)
        state = loading
        compose.waitForIdle()
        assertEquals("open while the list loads again", 0, backs)
        state = failed
        compose.waitForIdle()
        assertEquals("open while the list failed to load", 0, backs)
        state = holds
        compose.waitForIdle()
        assertEquals(0, backs)
        state = without
        compose.waitForIdle()
        assertEquals("closes once when its item is no longer listed", 1, backs)
    }

    @Test
    fun `a task's screen closes when the task leaves the list`() =
        check(
            holds = TasksUiState.Ready(TaskBoard(listOf(task))),
            without = TasksUiState.Ready(TaskBoard(emptyList())),
            loading = TasksUiState.Loading,
            failed = TasksUiState.Failed(failure),
        ) { state, back -> TaskScreen(state, "t1", TasksActions(onBack = back)) }

    @Test
    fun `a user's screen closes when the user leaves the list`() =
        check(
            holds = UsersUiState.Ready(listOf(user), emptyList(), me = "u0"),
            without = UsersUiState.Ready(emptyList(), emptyList(), me = "u0"),
            loading = UsersUiState.Loading,
            failed = UsersUiState.Failed(failure),
        ) { state, back -> UserScreen(state, "u1", UsersActions(onBack = back)) }

    @Test
    fun `a plugin's screen closes when the plugin leaves the list`() =
        check(
            holds = PluginsUiState.Ready(listOf(plugin)),
            without = PluginsUiState.Ready(emptyList()),
            loading = PluginsUiState.Loading,
            failed = PluginsUiState.Failed(failure),
        ) { state, back -> PluginScreen(state, "p1", PluginsActions(), back) }

    @Test
    fun `a stream's screen closes when the stream leaves the feed`() =
        check(
            holds = NowPlayingUiState.Ready(listOf(session)),
            without = NowPlayingUiState.Ready(emptyList()),
            loading = NowPlayingUiState.Loading,
            failed = NowPlayingUiState.Failed(failure),
        ) { state, back -> NowPlayingSessionScreen(state, "s1", NowPlayingActions(onBack = back)) }

    @Test
    fun `a package's screen closes when the package leaves the catalog`() =
        check(
            holds = CatalogUiState.Ready(listOf(pkg)),
            without = CatalogUiState.Ready(emptyList()),
            loading = CatalogUiState.Loading,
            failed = CatalogUiState.Failed(failure),
        ) { state, back -> PackageScreen(state, "AudioDB", CatalogActions(), back) }

    @Test
    fun `a library's home screen closes when the library leaves the list`() =
        check(
            holds = HomeLayoutUiState.Ready(listOf(view), HomeLayout()),
            without = HomeLayoutUiState.Ready(emptyList(), HomeLayout()),
            loading = HomeLayoutUiState.Loading,
            failed = HomeLayoutUiState.Failed(failure),
        ) { state, back -> HomeLayoutLibraryScreen(state, "v1", HomeLayoutActions(onBack = back)) }

    @Test
    fun `a fetcher's screen closes when the server stops offering it`() =
        check(
            holds = MetadataDefaultsUiState.Ready(JsonObject(emptyMap()), fetchers),
            without = MetadataDefaultsUiState.Ready(JsonObject(emptyMap()), listOf(TypeFetchers("Movie"))),
            loading = MetadataDefaultsUiState.Loading,
            failed = MetadataDefaultsUiState.Failed(failure),
        ) { state, back ->
            MetadataFetcherScreen(state, "Movie", OpenFetcher(FetcherKind.Metadata, "TheMovieDb"), MetadataDefaultsActions(onBack = back))
        }
}
