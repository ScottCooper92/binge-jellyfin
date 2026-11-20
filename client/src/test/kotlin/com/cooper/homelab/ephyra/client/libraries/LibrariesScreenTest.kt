package com.cooper.homelab.ephyra.client.libraries

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.ephyra.browse.LibraryKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Libraries tab's screen for each state, on a phone, and its skeleton against the loaded tiles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class LibrariesScreenTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val movies = LibraryTile("lib-movies", "Movies", LibraryKind.Movies, imageTag = "tag-1")
    private val shows = LibraryTile("lib-shows", "Shows", LibraryKind.TvShows, imageTag = null)

    private fun show(
        state: LibrariesUiState,
        refreshing: Boolean = false,
        imageUrl: (LibraryTile, Int) -> String? = { _, _ -> null },
        onOpen: (LibraryTile) -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        rule.setContent { Screen(state, refreshing, imageUrl, onOpen, onRefresh) }
        rule.waitForIdle()
    }

    @Composable
    private fun Screen(
        state: LibrariesUiState,
        refreshing: Boolean = false,
        imageUrl: (LibraryTile, Int) -> String? = { _, _ -> null },
        onOpen: (LibraryTile) -> Unit = {},
        onRefresh: () -> Unit = {},
        fontScale: Float = 1f,
    ) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            BingeExpressiveTheme {
                LibrariesScreen(state, refreshing, imageUrl, onOpen, onRefresh, reselect = "libraries")
            }
        }
    }

    @Test
    fun `each library is a tile with its name, and a tap opens it`() {
        val opened = mutableListOf<LibraryTile>()
        show(LibrariesUiState.Ready(listOf(movies, shows)), onOpen = { opened += it })
        rule.onNodeWithContentDescription("Movies").assertIsDisplayed()
        rule.onNodeWithContentDescription("Shows").performClick()
        assertEquals(listOf(shows), opened)
    }

    @Test
    fun `a line under the tiles says other libraries are not shown yet, only when some are hidden`() {
        show(LibrariesUiState.Ready(listOf(movies, shows), othersHidden = true))
        rule.onNodeWithText("Other libraries on this server aren't shown yet.").assertIsDisplayed()
    }

    @Test
    fun `with nothing hidden there is no such line`() {
        show(LibrariesUiState.Ready(listOf(movies, shows)))
        rule.onNodeWithText("Other libraries on this server aren't shown yet.").assertDoesNotExist()
    }

    @Test
    fun `a tile asks for its picture only when the library has one`() {
        val asked = mutableListOf<String>()
        show(
            LibrariesUiState.Ready(listOf(movies, shows)),
            imageUrl = { tile, _ ->
                asked += tile.id
                null
            },
        )
        assertEquals(listOf("lib-movies"), asked.distinct())
    }

    @Test
    fun `no libraries to list say so`() {
        show(LibrariesUiState.Empty)
        rule.onNodeWithText("Movie, series and collection libraries on this server will appear here.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a failure another go can fix offers Try again, and it asks for a refresh`() {
        var refreshed = 0
        show(LibrariesUiState.Error(ServerFailure.Unreachable()), onRefresh = { refreshed++ })
        rule.onNodeWithText("Try again").performClick()
        assertEquals(1, refreshed)
    }

    @Test
    fun `a failure no retry can fix offers none`() {
        show(LibrariesUiState.Error(ServerFailure.NotPermitted))
        rule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `the loading state shows no tile`() {
        show(LibrariesUiState.Loading)
        rule.onNodeWithContentDescription("Movies").assertDoesNotExist()
    }

    @Test
    fun `the skeleton holds the first tile where it lands`() = checkGeometry(1f)

    @Test
    fun `at a larger font the skeleton still holds the first tile where it lands`() = checkGeometry(2f)

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet the skeleton holds the first tile where it lands`() = checkGeometry(1f)

    private fun checkGeometry(fontScale: Float) {
        rule.assertSkeletonReservesGeometry(anchors = listOf(LayoutAnchors.section(FIRST_TILE_ANCHOR)), checkSize = true) { resolved ->
            Screen(
                if (resolved) LibrariesUiState.Ready(listOf(movies, shows, movies.copy(id = "c"))) else LibrariesUiState.Loading,
                fontScale = fontScale,
            )
        }
    }
}

private const val PHONE = "w411dp-h891dp-xhdpi"
private const val TABLET = "w840dp-h1280dp-xhdpi"
