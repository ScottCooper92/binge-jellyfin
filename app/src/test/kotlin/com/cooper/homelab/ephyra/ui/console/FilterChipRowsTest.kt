package com.cooper.homelab.ephyra.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.server.LogFile
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Filter chips are one row that scrolls, never a block that wraps onto a second line, so a larger font does not push the controls
 * below them down. The text is measured with the real font metrics, which the legacy graphics mode does not do.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp", fontScale = 2f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FilterChipRowsTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun top(text: String): Float = compose.onNodeWithText(text).top()

    private fun SemanticsNodeInteraction.top(): Float = getUnclippedBoundsInRoot().top.value

    @Test
    fun `the new trigger's kind chips are on one row at twice the font size`() {
        compose.setContent { JellyfinTheme { AddTriggerForm(TasksActions()) } }
        // A row that wraps has no horizontal scroll, and puts the later chips lower than the first.
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasText("Daily")))
            .assertExists()
        assertEquals(top("Daily"), top("Weekly"), 1f)
    }

    @Test
    fun `a log's level chips are on one row at twice the font size`() {
        val files = listOf(LogFile("log_20261031.log", 4096, 1_793_570_400_000L))
        compose.setContent {
            JellyfinTheme {
                LogScreen(
                    LogsUiState.Ready(files, OpenLog("log_20261031.log", LogContent.Ready(emptyList(), truncated = false))),
                    "log_20261031.log",
                    LogsActions(),
                )
            }
        }
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyDescendant(hasText("Info")))
            .assertExists()
        assertEquals(top("Info"), top("Warning"), 1f)
    }
}
