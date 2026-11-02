package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.cooper.binge.companion.jellyfin.server.CatalogPackage
import com.cooper.binge.companion.jellyfin.server.PackageVersion
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Plugins list keeps where the user scrolled to, whatever happens to the rows or the tab around it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PluginsListStateTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val version = PackageVersion("1.0.0.0", "A change.", "10.10.0.0", "Jellyfin Stable", "https://r.example/m.json")
    private val packages =
        (0 until PACKAGES).map { n ->
            CatalogPackage("id$n", "Package $n", "Does thing $n.", "", "someone", "Category $n", listOf(version))
        }
    private val far = "Package $FAR"

    private var catalog by mutableStateOf(CatalogUiState.Ready(packages))

    private fun show() =
        compose.setContent {
            JellyfinTheme {
                PluginsScreen(
                    installed = PluginsUiState.Ready(emptyList()),
                    catalog = catalog,
                    repositories = RepositoriesUiState.Ready(emptyList()),
                    actions = PluginsActions(),
                    catalogActions = CatalogActions(onOpen = { catalog = catalog.copy(open = it) }),
                    repositoryActions = RepositoriesActions(),
                    onBack = {},
                    initialTab = CATALOG_TAB,
                )
            }
        }

    @Test
    fun `a second Ready with the same rows leaves the catalog where it was scrolled`() {
        show()
        scrollFar()
        compose.onNodeWithText(far).assertIsDisplayed()
        catalog = CatalogUiState.Ready(packages.map { it.copy() })
        compose.waitForIdle()
        compose.onNodeWithText(far).assertIsDisplayed()
        compose.onNodeWithText("Package 0").assertDoesNotExist()
    }

    @Test
    fun `switching to another tab and back keeps the catalog where it was scrolled`() {
        show()
        scrollFar()
        compose.onNodeWithText("Installed").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Catalog").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(far).assertIsDisplayed()
    }

    @Test
    fun `opening a package and coming back keeps the catalog where it was scrolled`() {
        show()
        scrollFar()
        compose.onNodeWithText(far).performClick()
        compose.waitForIdle()
        catalog = catalog.copy(open = null)
        compose.waitForIdle()
        compose.onNodeWithText(far).assertIsDisplayed()
    }

    @Test
    fun `swiping moves between the tabs and the tab row follows, and a tap on a tab moves the pager`() {
        show()
        compose.onNodeWithText("Catalog").assertIsSelected()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Repositories").assertIsSelected()
        compose.onNodeWithText("Package 0").assertDoesNotExist()
        compose.onRoot().performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Catalog").assertIsSelected()
        compose.onNodeWithText("Installed").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Installed").assertIsSelected()
        compose.onNodeWithText("Package 0").assertDoesNotExist()
    }

    private fun scrollFar() {
        // The pager scrolls to an index too, so the list is the innermost one.
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToIndex(FAR)
        compose.waitForIdle()
    }

    private companion object {
        const val PACKAGES = 30
        const val FAR = 20
        const val CATALOG_TAB = 1
    }
}
