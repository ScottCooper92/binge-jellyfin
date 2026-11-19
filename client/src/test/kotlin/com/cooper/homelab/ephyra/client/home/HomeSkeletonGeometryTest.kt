package com.cooper.homelab.ephyra.client.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.homelab.ephyra.browse.BrowseItem
import com.cooper.homelab.ephyra.browse.ImageKind
import com.cooper.homelab.ephyra.browse.ItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home's skeleton reserves what the loaded page fills, in position and size: the hero and the first row. A plate that starts at the same
 * corner but is shorter would leave every row after it landing somewhere else, and a larger font grows a card's caption, not its poster,
 * so the check is made at the larger scales too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeSkeletonGeometryTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the skeleton holds the hero and the first row where they land`() = check()

    @Test
    @Config(qualifiers = TABLET)
    fun `on a tablet the skeleton holds the hero and the first row where they land`() = check()

    @Test
    @Config(qualifiers = PHONE)
    fun `at a larger font the skeleton still holds the first row where it lands`() = check(LARGE_FONT)

    @Test
    @Config(qualifiers = PHONE)
    fun `at the largest font the skeleton still holds the first row where it lands`() = check(LARGEST_FONT)

    private fun check(fontScale: Float = 1f) {
        val anchors = listOf(LayoutAnchors.Hub.HERO, LayoutAnchors.Hub.FIRST_CAROUSEL).map(LayoutAnchors::section)
        rule.assertSkeletonReservesGeometry(anchors = anchors, checkSize = true) { resolved ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BingeExpressiveTheme {
                    HomeScreen(
                        state = if (resolved) ready() else HomeUiState.Loading,
                        imageUrl = { _, _ -> null },
                        onOpen = {},
                        onResume = {},
                        onRetry = {},
                    )
                }
            }
        }
    }

    private fun ready(): HomeUiState {
        val items =
            List(CARDS) {
                BrowseItem(
                    id = "m$it",
                    name = "Movie $it",
                    kind = ItemKind.Movie,
                    year = 2020,
                    imageTags = mapOf(ImageKind.Primary to "p$it", ImageKind.Backdrop to "b$it"),
                )
            }
        val rows = listOfNotNull(homeRow(HomeRowKey.Latest("lib", "Movies"), items))
        return homeState(rows, pending = 0)
    }

    private companion object {
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val TABLET = "w840dp-h1280dp-xhdpi"
        const val CARDS = 6
        const val LARGE_FONT = 1.3f
        const val LARGEST_FONT = 2f
    }
}
