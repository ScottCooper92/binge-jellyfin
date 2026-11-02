package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.cooper.binge.companion.jellyfin.flags.Flags
import com.cooper.binge.companion.jellyfin.flags.LocalFlags
import com.cooper.binge.companion.jellyfin.server.TmdbCoverage
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the note under the server card says about the titles with no TMDB id, and when it says nothing. */
@RunWith(RobolectricTestRunner::class)
class TmdbCoverageNoteTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(
        coverage: TmdbCoverage?,
        flags: Flags = Flags(),
    ) {
        compose.setContent { CompositionLocalProvider(LocalFlags provides flags) { JellyfinTheme { TmdbCoverageNote(coverage) } } }
    }

    @Test
    fun `it counts the titles with no id, names the fix, and names no Binge while the flag is off`() {
        show(TmdbCoverage(titles = 20, withoutTmdbId = 3))
        compose.onNodeWithText("3 of 20 titles have no TMDB id").assertExists()
        compose.onNodeWithText("They cannot be matched by TMDB id.", substring = true).assertExists()
        compose.onNodeWithText("Turn on TMDb as a metadata downloader", substring = true).assertExists()
        compose.onAllNodesWithText("Binge", substring = true).assertCountEquals(0)
    }

    @Test
    fun `one title reads in the singular`() {
        show(TmdbCoverage(titles = 20, withoutTmdbId = 1))
        compose.onNodeWithText("1 of 20 titles has no TMDB id").assertExists()
    }

    @Test
    fun `it names Binge when the flag is on`() {
        show(TmdbCoverage(titles = 20, withoutTmdbId = 3), Flags.AllOn)
        compose.onNodeWithText("Binge cannot find these.", substring = true).assertExists()
    }

    @Test
    fun `it says nothing when every title has an id, or the count is not known`() {
        show(TmdbCoverage(titles = 20, withoutTmdbId = 0))
        compose.onAllNodesWithText("TMDB id", substring = true).assertCountEquals(0)
    }

    @Test
    fun `an unknown count says nothing`() {
        show(null)
        compose.onAllNodesWithText("TMDB id", substring = true).assertCountEquals(0)
    }
}
