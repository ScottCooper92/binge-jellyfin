package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogsTitleTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Test
    fun `long pressing a shortened log title shows its full name until OK`() {
        val name = "FFmpeg.Transcode-2026-10-31_09-15-02_3f2a9c.log"
        val state = LogsUiState.Ready(emptyList(), OpenLog(name, LogContent.Ready(emptyList(), truncated = false)))
        compose.setContent { JellyfinTheme { LogScreen(state, name, LogsActions()) } }
        compose.onNodeWithText(name).assertDoesNotExist()
        compose.onNodeWithText("Shared as shown here, with keys and tokens blanked.").assertDoesNotExist()
        compose.onNodeWithText(name.middleEllipsised()).performTouchInput { longClick() }
        compose.onNodeWithText("Full log name").assertExists()
        compose.onNodeWithText(name).assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(name).assertDoesNotExist()
    }
}
