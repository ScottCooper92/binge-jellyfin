package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.DisplayList
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
 * System Back steps back through a sheet's own steps, keeping what was typed or picked, and closes the sheet from the first:
 * adding a library, adding a repository, and the second half of a substitution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class StepFormsBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val closed = mutableListOf<String>()

    private fun systemBack() {
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
    }

    private fun at(title: String) = compose.onNodeWithText(title).assertExists()

    @Test
    fun `adding a library steps back through its steps and closes from the name`() {
        compose.setContent {
            JellyfinTheme {
                AddLibrarySheet(
                    names = emptyList(),
                    saving = false,
                    failed = null,
                    folders = FolderBrowseState.Loading,
                    folderActions = FolderActions(),
                    onAdd = { _, _, _ -> },
                    onDismiss = { closed += "library" },
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Films")
        compose.onNodeWithText("Next").performClick()
        at("What is in this library?")
        compose.onNodeWithText("Movies").performClick()
        at("Choose a folder")
        compose.onNodeWithText("Type a path").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Choose a folder").assertCountEquals(0)

        systemBack()
        at("Choose a folder")
        systemBack()
        at("What is in this library?")
        systemBack()
        at("Library name")
        compose.onNodeWithText("Films").assertExists()
        assertEquals(emptyList<String>(), closed)

        systemBack()
        assertEquals(listOf("library"), closed)
    }

    @Test
    fun `adding a repository steps back from its address to its name, keeping the name`() {
        compose.setContent {
            JellyfinTheme {
                AddRepositorySheet(saving = false, actions = RepositoriesActions(onAskAdd = { closed += "repository $it" }))
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Mine")
        compose.onNodeWithText("Next").performClick()
        at("Repository address")

        systemBack()
        at("Repository name")
        compose.onNodeWithText("Mine").assertExists()
        assertEquals(emptyList<String>(), closed)

        systemBack()
        assertEquals(listOf("repository false"), closed)
    }

    @Test
    fun `the second half of a substitution steps back to the first, keeping it`() {
        compose.setContent {
            JellyfinTheme {
                DisplaySheets(
                    LibraryDisplayUiState.Ready(JsonObject(emptyMap()), sheet = DisplaySheet.NewEntry),
                    LibraryDisplayActions(onSheet = { closed += "sheet $it" }),
                    FolderBrowseState.Loading,
                    FolderActions(),
                    editing = DisplayList.Substitutions,
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("colour")
        compose.onNodeWithText("Next").performClick()
        at("With")

        systemBack()
        at("Replace")
        compose.onNodeWithText("colour").assertExists()
        assertEquals(emptyList<String>(), closed)

        systemBack()
        assertEquals(listOf("sheet null"), closed)
    }
}
