package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.server.PackageVersion
import com.cooper.binge.companion.jellyfin.server.PluginStatus
import com.cooper.binge.companion.jellyfin.server.ServerPlugin
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1200dp-h800dp")
// Native text measurement makes the long changelog scroll, as it does on a device.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PluginsPaneStateTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val plugins =
        listOf(
            ServerPlugin("a", "AniDB", "1.0", "Anime metadata.", PluginStatus.Active, true, true),
            ServerPlugin("b", "Playback Reporting", "1.0", "Playback reports.", PluginStatus.Disabled, true, false),
        )
    private val version = PackageVersion("1.0", "New release.", "10.10.0", "Stable", "https://repo.example/manifest.json")
    private val packages =
        listOf(
            CatalogPackage("a", "Tuner Tools", "Find tuners.", "Find and manage tuners.", "someone", "General", listOf(version)),
            CatalogPackage("b", "Metadata Helpers", "Fetch metadata.", "Fetch more metadata.", "someone", "Metadata", listOf(version)),
        )
    private var installed by mutableStateOf(PluginsUiState.Ready(plugins))
    private var catalog by mutableStateOf(CatalogUiState.Ready(packages, server = JellyfinVersion.parse("10.10.7")))
    private var split by mutableStateOf(true)
    private var left = 0
    private var picked: CatalogPackage? = null
    private lateinit var positions: PluginsListStates

    private fun show(tab: Int = 0) = compose.setContent { Screen(tab) }

    @Composable
    private fun Screen(tab: Int) {
        JellyfinTheme {
            CompositionLocalProvider(LocalPageSpansTwoPanes provides split, LocalIsSinglePaneNav provides !split) {
                BackHandler { left++ }
                val lists = rememberPluginsListStates(installed.open, catalog.open)
                positions = lists
                PluginsScreen(
                    installed,
                    catalog,
                    RepositoriesUiState.Ready(emptyList()),
                    PluginsActions(onOpen = { installed = installed.copy(open = it?.id) }),
                    CatalogActions(
                        onOpen = { catalog = catalog.copy(open = it) },
                        onInstall = { picked = it },
                        onCancel = { picked = it },
                    ),
                    RepositoriesActions(),
                    onBack = { left++ },
                    initialTab = tab,
                    listStates = lists,
                )
            }
        }
    }

    @Test
    fun `an installed plugin opens beside its marked row while the tabs and page title stay`() {
        show()
        compose.onNodeWithText("AniDB").performClick()
        compose.onNodeWithText("Plugins").assertIsDisplayed()
        compose.onNodeWithText("Installed").assertIsDisplayed()
        compose.onNodeWithText("Playback Reporting").assertIsDisplayed()
        compose.onAllNodesWithText("AniDB").assertCountEquals(2)
        compose.onAllNodes(hasText("AniDB") and isSelected()).assertCountEquals(1)
        compose.onNodeWithText("Enabled").assertIsDisplayed()
    }

    @Test
    fun `the empty detail asks for a selection and Back closes an open plugin before its page`() {
        show()
        compose.onNodeWithText("Select a plugin or a package to see its details.").assertIsDisplayed()
        compose.onNodeWithText("AniDB").performClick()
        compose.onNodeWithText("Enabled").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Select a plugin or a package to see its details.").assertIsDisplayed()
        assertEquals(0, left)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, left)
    }

    @Test
    fun `a catalog package opens beside its list and its Install button belongs to the detail pane`() {
        show(tab = 1)
        compose.onNodeWithText("Tuner Tools").performClick()
        compose.onNodeWithText("Metadata Helpers").assertIsDisplayed()
        compose.onAllNodes(hasText("Tuner Tools") and isSelected()).assertCountEquals(1)
        val install = compose.onNodeWithText("Install").getUnclippedBoundsInRoot()
        val list = compose.onNodeWithText("Metadata Helpers").getUnclippedBoundsInRoot()
        assertTrue(install.left > list.right)
        compose.onNodeWithText("Install").performClick()
        assertEquals(packages.first(), picked)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Select a plugin or a package to see its details.").assertIsDisplayed()
        compose.onNodeWithText("Metadata Helpers").assertIsDisplayed()
        assertEquals(0, left)
    }

    @Test
    fun `installing replaces Install with Cancel in the open package's pane`() {
        catalog = catalog.copy(open = "Tuner Tools", installing = mapOf("Tuner Tools" to "install-id"))
        show(tab = 1)
        compose.onNodeWithText("Install").assertDoesNotExist()
        compose.onNodeWithText("Cancel install").performClick()
        assertEquals(packages.first(), picked)
    }

    @Test
    fun `narrowing and widening keeps the catalog tab, selection and detail scroll`() {
        val long = scrollablePackage()
        catalog = catalog.copy(packages = listOf(long, packages.last()), open = long.name)
        show(tab = 1)
        scrollDetail()
        val position = compose.runOnIdle { catalogPosition() }
        assertTrue(position.first > 0 || position.second > 0)
        compose.runOnIdle { split = false }
        compose.onNodeWithText("Metadata Helpers").assertDoesNotExist()
        compose.onNodeWithText("Tuner Tools").assertIsDisplayed()
        compose.runOnIdle { assertEquals(position, catalogPosition()) }
        compose.runOnIdle { split = true }
        compose.onNodeWithText("Catalog").assertIsDisplayed()
        compose.onNodeWithText("Metadata Helpers").assertIsDisplayed()
        compose.runOnIdle { assertEquals(position, catalogPosition()) }
    }

    @Test
    fun `switching tabs shows that tab's detail and the repositories keep their create footer`() {
        installed = installed.copy(open = "a")
        catalog = catalog.copy(open = "Tuner Tools")
        show()
        compose.onNodeWithText("Enabled").assertIsDisplayed()
        compose.onNodeWithText("Catalog").performClick()
        compose.onNodeWithText("Install").assertIsDisplayed()
        compose.onNodeWithText("Repositories").performClick()
        compose.onNodeWithText("Add repository").assertIsDisplayed()
        compose.onNodeWithText("Installed").performClick()
        compose.onNodeWithText("Enabled").assertIsDisplayed()
    }

    private fun scrollDetail() {
        val detail =
            hasScrollToIndexAction() and
                hasAnyDescendant(hasText("Made by")) and
                hasAnyDescendant(hasText("Metadata Helpers")).not()
        compose.onNode(detail).performScrollToIndex(1)
    }

    private fun scrollablePackage() =
        packages.first().copy(
            versions = listOf(version.copy(changelog = "Release details. ".repeat(2000))),
        )

    private fun catalogPosition() = positions.catalogDetail.firstVisibleItemIndex to positions.catalogDetail.firstVisibleItemScrollOffset

    @Test
    fun `restoring a resized catalog keeps its tab, package and detail position`() {
        val long = scrollablePackage()
        catalog = catalog.copy(packages = listOf(long, packages.last()), open = long.name)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Screen(1) }
        scrollDetail()
        val position = compose.runOnIdle { catalogPosition() }
        assertTrue(position.first > 0 || position.second > 0)
        compose.runOnIdle { split = false }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Tuner Tools").assertIsDisplayed()
        compose.runOnIdle { assertEquals(position, catalogPosition()) }
        compose.runOnIdle { split = true }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Catalog").assertIsSelected()
        compose.onNodeWithText("Metadata Helpers").assertIsDisplayed()
        compose.runOnIdle { assertEquals(position, catalogPosition()) }
    }
}
