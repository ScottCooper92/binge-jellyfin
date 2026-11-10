package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A refusal is a snackbar, unless the server's own reason is too long for one: then it is a message dialog with the whole
 * reason and one OK. At 1.3x and 2x font, nothing is cut.
 */
@RunWith(RobolectricTestRunner::class)
class RefusedMessageTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val longReason = "Library path /mnt/media/Movies does not exist or is not readable by the server."

    private fun show(
        failure: () -> ConsoleFailure?,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            JellyfinTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    val host = remember { SnackbarHostState() }
                    Box { SnackbarHost(host) }
                    RefusedMessage(failure(), host)
                }
            }
        }
    }

    @Test
    fun `a long server reason is a message dialog, in full, that OK closes`() {
        var failure by mutableStateOf<ConsoleFailure?>(ConsoleFailure.refused(longReason))
        show({ failure })
        compose.onNodeWithText("Not saved").assertIsDisplayed()
        compose.onNodeWithText(longReason).assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(longReason).assertDoesNotExist()
        // A later refusal with the same reason, once the page has cleared the failure, says so again.
        failure = null
        compose.waitForIdle()
        failure = ConsoleFailure.refused(longReason)
        compose.onNodeWithText(longReason).assertIsDisplayed()
    }

    private fun assertAllOnScreen(fontScale: Float) {
        show({ ConsoleFailure.refused(longReason) }, fontScale = fontScale)
        compose.onNodeWithText("Not saved").assertIsDisplayed()
        compose.onNodeWithText(longReason).assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun `at 1_3x font the long reason and OK are all on screen`() = assertAllOnScreen(1.3f)

    @Test
    fun `at 2x font the long reason and OK are all on screen`() = assertAllOnScreen(2f)

    @Test
    fun `a short reason stays a snackbar`() {
        show({ ConsoleFailure.refused("Path does not exist") })
        compose.onNodeWithText("Not saved. Path does not exist").assertIsDisplayed()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun `a refusal with no reason of the servers stays a snackbar with the generic line`() {
        show({ ConsoleFailure.Refused })
        compose.onNodeWithText("Not saved.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("OK").assertDoesNotExist()
    }
}
