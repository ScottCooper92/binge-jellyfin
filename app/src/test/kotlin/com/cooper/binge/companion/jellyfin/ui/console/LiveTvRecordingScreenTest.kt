package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveTvRecordingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val config: JsonObject =
        Json
            .parseToJsonElement(
                """{"GuideDays":7,"RecordingPath":"/recordings","MovieRecordingPath":"","SeriesRecordingPath":"/tv",""" +
                    """"PrePaddingSeconds":0,""" +
                    """"PostPaddingSeconds":180,"EnableRecordingSubfolders":false,"EnableOriginalAudioWithEncodedRecordings":true,""" +
                    """"SaveRecordingNFO":true,"SaveRecordingImages":false}""",
            ).jsonObject

    private fun show(
        state: LiveTvRecordingUiState,
        actions: LiveTvRecordingActions = LiveTvRecordingActions(),
    ) = compose.setContent { JellyfinTheme { LiveTvRecordingScreen(state, actions) } }

    @Test
    fun `each folder shows its path, or that it is not set, with a note that recordings are not moved`() {
        show(LiveTvRecordingUiState.Ready(config))
        compose.onNodeWithText("/recordings").assertIsDisplayed()
        compose.onNodeWithText("/tv").assertIsDisplayed()
        compose.onNodeWithText("Not set").assertIsDisplayed()
        compose.onNodeWithText("Changing a folder does not move recordings already made.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `padding reads in minutes, from the seconds the server stores`() {
        show(LiveTvRecordingUiState.Ready(config))
        compose.onNodeWithText("0 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tapping a switch sends its key with the opposite value`() {
        val sent = mutableListOf<Pair<String, JsonElement>>()
        show(LiveTvRecordingUiState.Ready(config), LiveTvRecordingActions(onSet = { key, value -> sent += key to value }))
        compose.onNodeWithText("A folder for each series").performScrollTo().performClick()
        compose.onNodeWithText("Keep the original audio").performScrollTo().performClick()
        assertEquals(
            listOf(
                "EnableRecordingSubfolders" to JsonPrimitive(true),
                "EnableOriginalAudioWithEncodedRecordings" to JsonPrimitive(false),
            ),
            sent,
        )
    }

    @Test
    fun `a folder row and a number row open their sheets`() {
        val opened = mutableListOf<RecordingSheet?>()
        show(LiveTvRecordingUiState.Ready(config), LiveTvRecordingActions(onSheet = { opened += it }))
        compose.onNodeWithText("Series recordings").performClick()
        compose.onNodeWithText("Stop late").performScrollTo().performClick()
        assertEquals(listOf(RecordingSheet.Folder(RecordingFolder.Series), RecordingSheet.Number(RecordingNumber.PostPadding)), opened)
    }

    @Test
    fun `a release without metadata files and images does not show them`() {
        val older = JsonObject(config - "SaveRecordingNFO" - "SaveRecordingImages")
        show(LiveTvRecordingUiState.Ready(older))
        compose.onNodeWithText("A folder for each series").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save metadata files").assertDoesNotExist()
        compose.onNodeWithText("Save images").assertDoesNotExist()
    }

    @Test
    fun `a guide length of none set reads as automatic`() {
        show(LiveTvRecordingUiState.Ready(JsonObject(config + ("GuideDays" to kotlinx.serialization.json.JsonNull))))
        compose.onNodeWithText("Automatic").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a failure offers Retry`() {
        var retried = 0
        show(LiveTvRecordingUiState.Failed(ConsoleFailure.Unreachable), LiveTvRecordingActions(onRetry = { retried++ }))
        compose.onNodeWithText("Try again", substring = true).performClick()
        assertEquals(1, retried)
    }
}
