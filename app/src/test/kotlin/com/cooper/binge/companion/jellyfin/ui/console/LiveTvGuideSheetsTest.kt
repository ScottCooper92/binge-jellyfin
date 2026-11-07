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
    fun `a Schedules Direct account has no address field, and says what can be done with it`() {
        form(GuideForm("g2", type = "SchedulesDirect", name = "alice"))
        compose.onNodeWithText("Address or path").assertDoesNotExist()
        compose.onNodeWithText("has a lineup", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Remove guide data").assertIsDisplayed()
    }

    @Test
    fun `the choice offers XMLTV and Schedules Direct`() {
        var xml = 0
        var sd = 0
        compose.setContent {
            JellyfinTheme {
                ChooseGuideContent(
                    LiveTvGuideActions(onAddXmlTv = { xml++ }, onAddSchedulesDirect = { sd++ }),
                )
            }
        }
        compose.onNodeWithText("XMLTV").performClick()
        compose.onNodeWithText("Schedules Direct").performClick()
        assertEquals(1, xml)
        assertEquals(1, sd)
    }

    @Test
    fun `an account with no lineup offers to finish the setup, above Remove`() {
        val finished = mutableListOf<String>()
        form(
            GuideForm("g2", type = "SchedulesDirect", name = "alice", lineupPending = true),
            LiveTvGuideActions(onFinishSetup = { finished += it }),
        )
        compose.onNodeWithText("no lineup is chosen yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Remove guide data").assertIsDisplayed()
        compose.onNodeWithText("Finish setup").performClick()
        assertEquals(listOf("g2"), finished)
    }

    @Test
    fun `a user without Live TV access is not offered Finish setup`() {
        compose.setContent {
            JellyfinTheme {
                GuideFormContent(
                    GuideForm("g2", type = "SchedulesDirect", name = "alice", lineupPending = true),
                    false,
                    LiveTvGuideActions(),
                    canFinishSetup = false,
                )
            }
        }
        compose.onNodeWithText("Finish setup").assertDoesNotExist()
        compose.onNodeWithText("Remove guide data").assertIsDisplayed()
    }

    @Test
    fun `an account with its lineup has no Finish setup, and can change its lineup`() {
        val changed = mutableListOf<String>()
        form(GuideForm("g2", type = "SchedulesDirect", name = "alice"), LiveTvGuideActions(onChangeLineup = { changed += it }))
        compose.onNodeWithText("Finish setup").assertDoesNotExist()
        compose.onNodeWithText("Change lineup").performClick()
        assertEquals(listOf("g2"), changed)
    }

    @Test
    fun `a user without Live TV access cannot change a lineup, and an XMLTV provider has none`() {
        compose.setContent {
            JellyfinTheme {
                GuideFormContent(
                    GuideForm("g2", type = "SchedulesDirect", name = "alice"),
                    false,
                    LiveTvGuideActions(),
                    canFinishSetup = false,
                )
            }
        }
        compose.onNodeWithText("Change lineup").assertDoesNotExist()
        compose.onNodeWithText("set up in Jellyfin", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Remove guide data").assertIsDisplayed()
    }

    @Test
    fun `a Schedules Direct account Change login sends its id and username`() {
        val asked = mutableListOf<Pair<String, String>>()
        val actions = LiveTvGuideActions(onChangeLogin = { id, name -> asked += id to name })
        form(GuideForm("g2", type = "SchedulesDirect", name = "alice"), actions)
        compose.onNodeWithText("Change login").performClick()
        assertEquals(listOf("g2" to "alice"), asked)
    }

    @Test
    fun `an unfinished account and a user without Live TV access can still change a login`() {
        compose.setContent {
            JellyfinTheme {
                GuideFormContent(
                    GuideForm("g2", type = "SchedulesDirect", name = "alice", lineupPending = true),
                    false,
                    LiveTvGuideActions(),
                    canFinishSetup = false,
                )
            }
        }
        compose.onNodeWithText("Change login").assertIsDisplayed()
    }

    @Test
    fun `an XMLTV provider has no login to change`() {
        form(GuideForm("g1", "/config/guide.xml", name = "xmltv"))
        compose.onNodeWithText("Change login").assertDoesNotExist()
    }

    @Test
    fun `an XMLTV provider is not offered a lineup`() {
        form(GuideForm("g1", "/config/guide.xml", name = "xmltv"))
        compose.onNodeWithText("Change lineup").assertDoesNotExist()
    }

    @Test
    fun `a provider with no lineup says so in its row`() {
        compose.setContent {
            JellyfinTheme {
                LiveTvScreen(
                    LiveTvUiState.Ready(
                        LiveTvSummary(providers = listOf(GuideProviderSummary("g2", "SchedulesDirect", "alice", lineupPending = true))),
                    ),
                    LiveTvActions(),
                )
            }
        }
        compose.onNodeWithText("Schedules Direct · setup not finished").assertIsDisplayed()
    }
}
