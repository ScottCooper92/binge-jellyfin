package com.cooper.homelab.ephyra.client.title

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.ShadowMeshSpecification
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.homelab.ephyra.browse.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The collection rail on a film's page: its See all control opens the collection's page, and nothing else on the page does. */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowMeshSpecification::class], qualifiers = "w411dp-h4000dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovieCollectionRailTest {
    @get:Rule
    val rule = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val part = TitleCard("m3", ItemKind.Movie, "The Longer Quiet", 2023, 7.0, null, false)
    private val film = sampleMovie().copy(collection = CollectionRow("box", "The Quiet Collection", listOf(part)))

    private fun show(movie: MovieUi): Pair<MutableList<String>, MutableList<TitleCard>> {
        val collections = mutableListOf<String>()
        val titles = mutableListOf<TitleCard>()
        rule.setContent {
            BingeExpressiveTheme {
                MovieScreen(
                    state = MovieUiState.Ready(movie),
                    imageUrl = { _, _ -> null },
                    actions =
                        MovieActions(
                            onBack = {},
                            onRetry = {},
                            onPlay = {},
                            onToggleWatched = {},
                            onToggleFavourite = {},
                            onOpenTitle = { titles += it },
                            onOpenPerson = {},
                            onOpenCollection = { collections += it },
                            onOpenLink = {},
                            onShare = { _, _ -> },
                        ),
                )
            }
        }
        return collections to titles
    }

    @Test
    fun `the collection rail's See all opens the collection by its id`() {
        val (collections, titles) = show(film)
        rule.onNodeWithContentDescription("See all in The Quiet Collection").performClick()
        assertEquals(listOf("box"), collections)
        assertEquals(emptyList<TitleCard>(), titles)
    }

    @Test
    fun `a part in the rail still opens its own film, not the collection`() {
        val (collections, titles) = show(film)
        rule.onNodeWithText("The Longer Quiet").performClick()
        assertEquals(listOf(part), titles)
        assertEquals(emptyList<String>(), collections)
    }
}
