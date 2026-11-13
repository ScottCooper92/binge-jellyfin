package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The review prompt is asked for on coming back to the home, never on loading the session or signing in. */
@RunWith(RobolectricTestRunner::class)
class ReviewPromptOnReturnToRootTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var signedIn by mutableStateOf(false)
    private var atRoot by mutableStateOf(true)
    private var asked = 0

    private fun host() =
        compose.setContent {
            ReviewPromptOnReturnToRoot(signedIn = signedIn, atRoot = atRoot, onReturnedToRoot = { asked++ })
        }

    @Test
    fun `loading the session and signing in do not ask`() {
        host()
        compose.runOnIdle { signedIn = true }
        compose.runOnIdle {}
        assertEquals(0, asked)
    }

    @Test
    fun `leaving the home and coming back asks once`() {
        signedIn = true
        host()
        compose.runOnIdle { atRoot = false }
        compose.runOnIdle { atRoot = true }
        compose.runOnIdle {}
        assertEquals(1, asked)
        compose.runOnIdle { atRoot = true }
        assertEquals(1, asked)
    }

    @Test
    fun `signing out over a page does not ask`() {
        signedIn = true
        host()
        compose.runOnIdle { atRoot = false }
        compose.runOnIdle { signedIn = false }
        compose.runOnIdle { atRoot = true }
        compose.runOnIdle { signedIn = true }
        compose.runOnIdle {}
        assertEquals(0, asked)
    }
}
