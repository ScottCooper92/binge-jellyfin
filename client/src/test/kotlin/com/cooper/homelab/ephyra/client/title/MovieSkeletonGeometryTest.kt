package com.cooper.homelab.ephyra.client.title

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.ShadowMeshSpecification
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.homelab.ephyra.browse.ImageKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The movie page's skeleton holds the hero, the stat rows and the overview where the loaded page puts them, at the size it gives them,
 * with the action row between the stats and the overview at its loaded height, so the overview lands where its plate stood. At the
 * expanded width the cinematic header carries the stats and the synopsis, so only the hero is held. Sizes are checked, so the graphics
 * mode is native: legacy mode measures text at one pixel per character.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMeshSpecification::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovieSkeletonGeometryTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val phoneAnchors =
        listOf(LayoutAnchors.Detail.HERO, LayoutAnchors.Detail.STATS, LayoutAnchors.Detail.OVERVIEW).map(LayoutAnchors::section)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the skeleton holds the hero, the stats and the overview where they land`() = check(phoneAnchors)

    @Test
    @Config(qualifiers = PHONE)
    fun `at a larger font the skeleton holds them where they land`() = check(phoneAnchors, LARGE_FONT)

    @Test
    @Config(qualifiers = PHONE)
    fun `at the largest font the skeleton holds them where they land`() = check(phoneAnchors, LARGEST_FONT)

    @Test
    @Config(qualifiers = TABLET)
    fun `at the expanded width the skeleton holds the hero where it lands`() =
        check(listOf(LayoutAnchors.section(LayoutAnchors.Detail.HERO)))

    private fun check(
        anchors: List<String>,
        fontScale: Float = 1f,
    ) {
        rule.assertSkeletonReservesGeometry(anchors = anchors, checkSize = true) { resolved ->
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BingeExpressiveTheme {
                    val snackbar = remember { SnackbarHostState() }
                    MovieScreen(
                        state = if (resolved) MovieUiState.Ready(sampleMovie()) else MovieUiState.Loading,
                        imageUrl = { _, _ -> null },
                        actions = noActions(),
                        snackbarHostState = snackbar,
                    )
                }
            }
        }
    }

    private companion object {
        const val PHONE = "w411dp-h891dp-xhdpi"
        const val TABLET = "w840dp-h1280dp-xhdpi"
        const val LARGE_FONT = 1.3f
        const val LARGEST_FONT = 2f
    }
}

internal fun noActions() =
    MovieActions(
        onBack = {},
        onRetry = {},
        onPlay = {},
        onToggleWatched = {},
        onToggleFavourite = {},
        onOpenTitle = {},
        onOpenPerson = {},
        onOpenCollection = {},
        onOpenLink = {},
        onShare = { _, _ -> },
    )

/** A film with every part the page shows, none of it loading a picture. */
internal fun sampleMovie(
    versions: Int = 1,
    play: PlayAction = PlayAction(PlayKind.Play, null),
) = MovieUi(
    id = "m1",
    title = "The Long Quiet",
    tagline = "Some silences are loud.",
    genres = listOf("Drama", "Mystery"),
    backdrop = TitleImage("m1", ImageKind.Backdrop, "b"),
    poster = TitleImage("m1", ImageKind.Primary, "p"),
    stats = MovieStats(rating = 7.8, year = 2021, runtimeMinutes = 118, certification = "PG-13"),
    play = play,
    watched = false,
    favourite = false,
    overview = "A lighthouse keeper receives a letter that should not exist. ".repeat(4).trim(),
    facts = TitleFacts("Le Grand Silence", listOf("Harbour Films"), "2021-04-09T00:00:00.0000000Z"),
    versions = List(versions) { VersionUi("v$it", null, "mkv", Resolution.FullHd, null, null, emptyList(), emptyList(), null, null) },
    cast = listOf(PersonUi("p1", "Amara Okafor", "Lead", null)),
    crew = listOf(PersonUi("p2", "Jonas Weil", null, null, CrewJob.Director)),
    collection = null,
    similar = listOf(TitleCard("m2", com.cooper.homelab.ephyra.browse.ItemKind.Movie, "Night Market", 2019, 7.1, null, false)),
)
