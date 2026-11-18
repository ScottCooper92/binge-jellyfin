package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The server card's "Not encrypted" mark, which is the way to take back an agreement to plain HTTP where there is one. */
@RunWith(RobolectricTestRunner::class)
class TakeBackChipTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    // What a screen reader says: the chip's status first, then what tapping it does.
    private val description = "Not encrypted. Stop trusting this address"

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun show(
        baseUrl: String,
        consentHost: String?,
        taken: MutableList<Unit> = mutableListOf(),
    ) = compose.setContent {
        JellyfinTheme {
            val session = JellyfinSession(baseUrl, "Server", "a1", "10.11.6", "u1", "sam", "not-a-token")
            ServerHomeCard(UiState.SignedIn(session, consentHost = consentHost), onTakeBack = { taken += Unit })
        }
    }

    @Test
    fun `where the user agreed to plain HTTP the mark is a button that asks to take it back`() {
        val taken = mutableListOf<Unit>()
        show("http://media.example.com:8096", "media.example.com", taken)
        // A button that says its status and its action, not a bare tap target.
        compose.onNode(hasClickAction() and hasContentDescription(description) and isButton).assertExists()
        compose.onNodeWithContentDescription(description).performClick()
        assertEquals(1, taken.size)
    }

    @Test
    fun `a plain HTTP server on the local network has nothing to take back, so its mark is only a label`() {
        val taken = mutableListOf<Unit>()
        show("http://192.168.1.2:8096", consentHost = null, taken = taken)
        compose.onNodeWithText("Not encrypted").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Not encrypted")).assertDoesNotExist()
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
        assertEquals(0, taken.size)
    }

    @Test
    fun `an encrypted server has no mark`() {
        show("https://media.example.com", consentHost = null)
        compose.onNodeWithText("Not encrypted").assertDoesNotExist()
    }
}
