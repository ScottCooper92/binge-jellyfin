package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.R
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The console's fields offer the design system's clear button while focused with text, and a secret field never does. */
@RunWith(RobolectricTestRunner::class)
class ConsoleTextFieldTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val clear get() = compose.activity.getString(R.string.field_clear)

    private fun setFields() {
        compose.setContent {
            JellyfinTheme {
                var name by remember { mutableStateOf("") }
                var key by remember { mutableStateOf("hunter2") }
                Column {
                    ConsoleTextField(value = name, onValueChange = { name = it }, label = "Name")
                    ConsoleTextField(value = key, onValueChange = { key = it }, label = "Key", secret = true)
                }
            }
        }
    }

    @Test
    fun `a focused field with text offers a clear button that empties it`() {
        setFields()
        compose.onNodeWithContentDescription(clear).assertDoesNotExist()
        compose.onNodeWithText("Name").performTextInput("abc")
        compose.onNodeWithContentDescription(clear).performClick()
        compose.onNodeWithText("Name").assertTextEquals("Name", "")
        compose.onNodeWithContentDescription(clear).assertDoesNotExist()
    }

    @Test
    fun `a secret field never offers the clear button`() {
        setFields()
        compose.onNodeWithText("Key").performClick()
        compose.onNodeWithText("Key").performTextInput("x")
        compose.onNodeWithContentDescription(clear).assertDoesNotExist()
    }
}
