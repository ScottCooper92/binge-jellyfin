package com.cooper.homelab.ephyra.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.R
import com.cooper.homelab.ephyra.theme.JellyfinTvTheme
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.cooper.homelab.core.uiandroid.R as CoreR

/**
 * The television keeps its own consent screen, but the words it shares with the phone's question are the shared library's own
 * strings, read from the library, so the two cannot say different things. Only what is worded for a remote (the last point's title
 * and the reassurance) is the TV's. The screen is rendered and each library string is looked for on it.
 */
@RunWith(RobolectricTestRunner::class)
class TvConsentCopyTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val shared =
        listOf(
            CoreR.string.consent_kicker,
            CoreR.string.consent_title,
            CoreR.string.consent_subtitle,
            CoreR.string.consent_point_shared_title,
            CoreR.string.consent_point_shared_detail,
            CoreR.string.consent_point_never_title,
            CoreR.string.consent_point_never_detail,
            CoreR.string.consent_point_change_detail,
            CoreR.string.consent_accept,
            CoreR.string.consent_decline,
        )

    /** The page may set a word in capitals or fold a point into one node, so the text is matched whatever its case, in the unmerged tree. */
    private fun shown(id: Int) {
        compose.onNodeWithText(compose.activity.getString(id), ignoreCase = true, useUnmergedTree = true).assertExists()
    }

    private fun assertLibraryWordsShown() {
        compose.setContent { JellyfinTvTheme { TvConsentScreen(onChoice = {}) } }
        shared.forEach { shown(it) }
        shown(R.string.tv_consent_reassurance)
        // The last point's title is the TV's own, worded for a press and not a tap.
        assertNotEquals(
            compose.activity.getString(CoreR.string.consent_point_change_title),
            compose.activity.getString(R.string.tv_consent_point_change_title),
        )
    }

    @Test
    fun `the screen shows the library's words in English`() = assertLibraryWordsShown()

    @Test
    @Config(qualifiers = "es")
    fun `the screen shows the library's words in Spanish`() = assertLibraryWordsShown()
}
