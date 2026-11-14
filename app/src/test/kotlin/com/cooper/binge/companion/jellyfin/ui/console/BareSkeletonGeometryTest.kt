package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pages that load as the design system's bare skeleton column, and Licences, hold their first group where the settled page puts
 * it, so nothing jumps when the content arrives (#514).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class BareSkeletonGeometryTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val profile = JellyfinServerProfile.from("10.11.4")

    private val library = ServerLibrary("movies", "Movies", "movies", listOf("/media/movies"))

    private fun ComposeContentTestRule.holdsFirstGroup(page: @Composable (resolved: Boolean) -> Unit) =
        assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            checkStartEdge = true,
        ) { resolved -> JellyfinTheme { page(resolved) } }

    @Test
    fun `licences`() =
        compose.holdsFirstGroup { resolved ->
            LicencesScreen(
                state =
                    if (resolved) {
                        LicencesUiState.Ready(listOf(LicenceEntry("a", "Library", "1.0", listOf("Apache-2.0"), null)))
                    } else {
                        LicencesUiState.Loading
                    },
                onBack = {},
                onOpenWebsite = {},
            )
        }

    @Test
    fun `libraries`() =
        compose.holdsFirstGroup { resolved ->
            LibrariesScreen(
                state =
                    if (resolved) {
                        LibrariesUiState.Ready(listOf(library), TaskBoard(emptyList()))
                    } else {
                        LibrariesUiState.Loading
                    },
                actions = LibrariesActions(),
                openLibrary = null,
            )
        }

    @Test
    fun `plugins`() =
        compose.holdsFirstGroup { resolved ->
            PluginsScreen(
                installed =
                    if (resolved) {
                        PluginsUiState.Ready(listOf(ServerPlugin("p", "AniDB", "9.0.0.0", "", PluginStatus.Active, true, false)))
                    } else {
                        PluginsUiState.Loading
                    },
                catalog = CatalogUiState.Loading,
                repositories = RepositoriesUiState.Loading,
                actions = PluginsActions(),
                catalogActions = CatalogActions(),
                repositoryActions = RepositoriesActions(),
                onBack = {},
                openItem = null,
            )
        }

    @Test
    fun `a library`() =
        compose.holdsFirstGroup { resolved ->
            LibraryScreen(
                state = if (resolved) LibraryUiState.Ready(library, JsonObject(emptyMap())) else LibraryUiState.Loading,
                actions = LibraryActions(),
                profile = profile,
            )
        }

    @Test
    fun `library display`() =
        compose.holdsFirstGroup { resolved ->
            LibraryDisplayScreen(
                state = if (resolved) LibraryDisplayUiState.Ready(JsonObject(emptyMap())) else LibraryDisplayUiState.Loading,
                actions = LibraryDisplayActions(),
                profile = profile,
                openList = null,
            )
        }

    @Test
    fun `metadata defaults`() =
        compose.holdsFirstGroup { resolved ->
            MetadataDefaultsScreen(
                state = if (resolved) MetadataDefaultsUiState.Ready(JsonObject(emptyMap())) else MetadataDefaultsUiState.Loading,
                actions = MetadataDefaultsActions(),
                open = null,
            )
        }
}
