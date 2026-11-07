package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * A folder sheet's "Type a path" step is a page of the sheet, so system Back returns to the folder list first and closes the
 * sheet from there, on a phone as on a tablet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class FolderSheetBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val closed = mutableListOf<RecordingSheet?>()

    private fun show() =
        compose.setContent {
            JellyfinTheme {
                RecordingSheets(
                    LiveTvRecordingUiState.Ready(JsonObject(emptyMap()), sheet = RecordingSheet.Folder(RecordingFolder.Movies)),
                    LiveTvRecordingActions(onSheet = { closed += it }),
                    FolderBrowseState.Loading,
                    FolderActions(),
                )
            }
        }

    private fun systemBack() {
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back from the typed path returns to the folder list, and Back from there closes the sheet`() {
        show()
        compose.onAllNodesWithText("Choose a folder").onFirst().assertExists()
        compose.onNodeWithText("Type a path").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Choose a folder").assertCountEquals(0)
        compose.onAllNodesWithText("Movie recordings").onFirst().assertExists()

        systemBack()
        compose.onAllNodesWithText("Choose a folder").onFirst().assertExists()
        assertEquals("the sheet stays open for the step back", emptyList<RecordingSheet?>(), closed)

        systemBack()
        assertEquals("then Back closes it", listOf<RecordingSheet?>(null), closed)
    }

    @Test
    fun `the Back button of the typed path steps back the same way`() {
        show()
        compose.onNodeWithText("Type a path").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Back").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Choose a folder").onFirst().assertExists()
        assertEquals(emptyList<RecordingSheet?>(), closed)
    }
}
