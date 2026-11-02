package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What is typed into a form sheet survives a rotation. A task sheet is a bottom sheet on a portrait phone and a side sheet once
 * the window is wide, and the two draw their content at different places in the composition. `rememberSaveable` without a key
 * is found again only at the same place, so a form that used it lost what was typed when the phone turned. Each field of a sheet
 * form is saved under a key of its own, which does not depend on where it is drawn.
 *
 * The test puts a real form in two different places, as the two sheets do, and rebuilds it from its saved state in the second.
 * It does not include the sheets' own windows: the test rule does not restore a window's content at all, so a sheet shown in a
 * window is restored here neither with the key nor without it. That part is checked on a device.
 */
@RunWith(RobolectricTestRunner::class)
class SheetRotationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Where the form is drawn when the content is next built. A plain variable, not state: a rotation rebuilds the content from its
     * saved state, it does not recompose it where it stands, and a live recomposition would drop the form before it was saved.
     */
    private var sideSheet = false

    /** The place the bottom sheet draws its content, or the side sheet's: the same form, at a different position in the tree. */
    @Composable
    private fun WhereverTheSheetDrawsIt(content: @Composable () -> Unit) {
        if (sideSheet) Column { Box { content() } } else Box { content() }
    }

    /** Types [typed] into the field showing [hint], moves the form to the other place, and finds [typed] still there. */
    private fun survivesTheSwitch(
        hint: String,
        typed: String,
        content: @Composable () -> Unit,
    ) {
        val tester = StateRestorationTester(compose)
        tester.setContent { JellyfinTheme { WhereverTheSheetDrawsIt(content) } }
        compose.onNodeWithText(hint).performTextInput(typed)
        compose.onNodeWithText(typed).assertIsDisplayed()
        sideSheet = true
        tester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText(typed).assertIsDisplayed()
    }

    @Test
    fun `a repository name typed in the Add repository form survives the switch between the bottom and side sheet`() {
        survivesTheSwitch("A name to know it by", "Jellyfin Stable") { AddRepositoryForm(saving = false, actions = RepositoriesActions()) }
    }
}
