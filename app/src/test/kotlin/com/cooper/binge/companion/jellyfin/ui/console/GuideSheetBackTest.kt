package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Add guide data: a new XMLTV guide's form is a page over the choice it came from, so system Back returns to the choice and
 * closes the sheet from there; a form opened from the page's own row has no choice under it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class GuideSheetBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(GuideUiState())
    private val log = mutableListOf<String>()

    private val actions =
        LiveTvGuideActions(
            onAddXmlTv = {
                state = GuideUiState(form = GuideForm())
                log += "add"
            },
            onBackToChoice = {
                state = GuideUiState(choosing = true)
                log += "back to the choice"
            },
            onDismissChoice = {
                state = state.copy(choosing = false)
                log += "dismiss choice"
            },
            onDismissForm = {
                state = state.copy(form = null)
                log += "dismiss form"
            },
        )

    private fun show() = compose.setContent { JellyfinTheme { GuideSheet(state, actions) } }

    private fun systemBack() {
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back from a new guide's form returns to the choice, and Back from there closes the sheet`() {
        state = GuideUiState(choosing = true)
        show()
        compose.onNodeWithText("Add guide data").assertExists()
        compose.onNodeWithText("XMLTV").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add XMLTV guide").assertExists()

        systemBack()
        compose.onNodeWithText("Add guide data").assertExists()
        compose.onAllNodesWithText("Add XMLTV guide").assertCountEquals(0)
        assertEquals(listOf("add", "back to the choice"), log)

        systemBack()
        assertEquals(listOf("add", "back to the choice", "dismiss choice", "dismiss form"), log)
        compose.onAllNodesWithText("Add guide data").assertCountEquals(0)
    }

    @Test
    fun `a guide opened from the page's own row has no choice under it, so Back closes the sheet`() {
        state = GuideUiState(form = GuideForm(id = "p1", path = "/guide.xml"))
        show()
        compose.onNodeWithText("Edit guide data").assertExists()
        systemBack()
        assertEquals(listOf("dismiss choice", "dismiss form"), log)
        compose.onAllNodesWithText("Edit guide data").assertCountEquals(0)
    }
}
