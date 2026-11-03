package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.GuideProviderSummary
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveTvGuideSheetsTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val xmltv = GuideProviderSummary("g1", "xmltv", "/config/guide.xml")
    private val schedulesDirect = GuideProviderSummary("g2", "SchedulesDirect", "alice")
    private val summary = LiveTvSummary(providers = listOf(xmltv, schedulesDirect))

    private fun page(actions: LiveTvGuideActions) =
        compose.setContent { JellyfinTheme { LiveTvScreen(LiveTvUiState.Ready(summary), LiveTvActions(), guideActions = actions) } }

    private fun form(
        form: GuideForm,
        actions: LiveTvGuideActions = LiveTvGuideActions(),
    ) = compose.setContent { JellyfinTheme { GuideFormContent(form, false, actions) } }

    @Test
    fun `Add guide data and each provider's row open their forms`() {
        var added = 0
        val edited = mutableListOf<String>()
        page(LiveTvGuideActions(onAdd = { added++ }, onEdit = { edited += it.id }))
        compose.onNodeWithText("Add guide data").performClick()
        compose.onNodeWithText("/config/guide.xml").performClick()
        compose.onNodeWithText("alice").performClick()
        assertEquals(1, added)
        assertEquals(listOf("g1", "g2"), edited)
    }

    @Test
    fun `an XMLTV form takes an address or a path, and Add sends what was typed`() {
        val saved = mutableListOf<GuideForm>()
        form(GuideForm(), LiveTvGuideActions(onSave = { saved += it }))
        compose.onNodeWithText("Address or path").performTextInput("https://guide.example/x.xml")
        compose.onNodeWithText("Add").performClick()
        assertEquals("https://guide.example/x.xml", saved.single().path)
    }

    @Test
    fun `an existing guide offers Remove, which asks and does not remove`() {
        val asked = mutableListOf<String?>()
        form(GuideForm("g1", "/config/guide.xml", name = "guide"), LiveTvGuideActions(onAskRemove = { asked += it.id }))
        compose.onNodeWithText("Remove guide data").performClick()
        assertEquals(listOf<String?>("g1"), asked)
    }

    @Test
    fun `a new guide has no Remove`() {
        form(GuideForm())
        compose.onNodeWithText("Remove guide data").assertDoesNotExist()
    }

    @Test
    fun `a Schedules Direct account has no address field, and says where it is set up`() {
        form(GuideForm("g2", type = "SchedulesDirect", name = "alice"))
        compose.onNodeWithText("Address or path").assertDoesNotExist()
        compose.onNodeWithText("set up in Jellyfin", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Remove guide data").assertIsDisplayed()
    }
}
