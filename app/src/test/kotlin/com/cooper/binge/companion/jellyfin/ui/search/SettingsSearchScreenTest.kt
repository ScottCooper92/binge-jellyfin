package com.cooper.binge.companion.jellyfin.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsSearchScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var opened: ConsolePage? = null

    private fun show(admin: Boolean) {
        val account = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":$admin}""").jsonObject)
        compose.setContent {
            JellyfinTheme { SettingsSearchScreen(JellyfinServerProfile.from("10.11.6"), account, { opened = it }, {}) }
        }
    }

    @Test
    fun `before a query it says what to type`() {
        show(admin = true)
        compose.onNodeWithText("Type the name of a setting, or what it does.").assertExists()
    }

    @Test
    fun `a result says where the setting is, and opens its page`() {
        show(admin = true)
        compose.onNode(hasSetTextAction()).performTextInput("https port")
        compose.onAllNodesWithText("Advanced › Networking").onFirst().assertExists()
        compose.onNodeWithText("HTTPS port").performClick()
        assertEquals(ConsolePage.Network, opened)
    }

    @Test
    fun `a user is not offered what only an administrator may open`() {
        show(admin = false)
        compose.onNode(hasSetTextAction()).performTextInput("hardware")
        compose.onAllNodesWithText("Hardware acceleration").assertCountEquals(0)
        compose.onNodeWithText("No settings found").assertExists()
    }
}
