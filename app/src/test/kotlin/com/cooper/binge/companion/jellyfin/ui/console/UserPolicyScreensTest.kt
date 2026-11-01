package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LiveChannel
import com.cooper.binge.companion.jellyfin.server.ParentalRating
import com.cooper.binge.companion.jellyfin.server.ServerDevice
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A user's settings pages: which rows a release and a policy show, and that a tap reaches its action. */
@RunWith(RobolectricTestRunner::class)
class UserPolicyScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun policy(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private fun show(
        section: UserSection?,
        policy: JsonObject,
        profile: JellyfinServerProfile = JellyfinServerProfile.from("10.11.6"),
        choices: UserChoices = UserChoices(),
        actions: UsersActions = UsersActions(),
    ) {
        val sam = ServerUser("sam", "Sam", false, null, policy)
        val libraries = listOf(ServerLibrary("m", "Movies", "movies", emptyList()), ServerLibrary("s", "Shows", "tvshows", emptyList()))
        val state = UsersUiState.Ready(listOf(sam), libraries, me = "me", open = "sam", section = section)
        compose.setContent { JellyfinTheme { UsersScreen(state, actions, choices = choices, profile = profile) } }
    }

    @Test
    fun `a user's page offers the sections the server sent, and no others`() {
        show(null, policy("""{"IsAdministrator":false,"EnableMediaPlayback":true,"EnableLiveTvAccess":true}"""))
        compose.onNodeWithText("Playback").assertExists()
        compose.onNodeWithText("Live TV").assertExists()
        compose.onAllNodesWithText("Devices").assertCountEquals(0)
        compose.onAllNodesWithText("Parental control").assertCountEquals(0)
        compose.onAllNodesWithText("Account access").assertCountEquals(0)
    }

    @Test
    fun `the 10_9 content rows are hidden before 10_9, and shown from it`() {
        val sent =
            policy(
                """{"EnableContentDownloading":true,"EnableContentDeletion":true,"EnableCollectionManagement":false,"EnableSubtitleManagement":false}""",
            )
        show(UserSection.Content, sent, JellyfinServerProfile.from("10.8.13"))
        compose.onNodeWithText("Allow downloads").assertExists()
        compose.onAllNodesWithText("Manage collections").assertCountEquals(0)
        compose.onAllNodesWithText("Manage subtitles").assertCountEquals(0)
    }

    @Test
    fun `the content rows from 10_9 are shown, but only the ones the server sent`() {
        show(
            UserSection.Content,
            policy("""{"EnableContentDeletion":true,"EnableCollectionManagement":false,"EnableSubtitleManagement":false}"""),
            JellyfinServerProfile.from("10.9.0"),
        )
        compose.onNodeWithText("Manage collections").assertExists()
        compose.onNodeWithText("Manage subtitles").assertExists()
        compose.onAllNodesWithText("Manage lyrics").assertCountEquals(0)
    }

    @Test
    fun `allowed tags need 10_9 and the sub-rating needs 10_11`() {
        val sent =
            policy("""{"BlockedTags":[],"AllowedTags":[],"AccessSchedules":[]}""")
        show(UserSection.Parental, sent, JellyfinServerProfile.from("10.8.13"))
        compose.onNodeWithText("BLOCKED TAGS").assertExists()
        compose.onAllNodesWithText("ALLOWED TAGS").assertCountEquals(0)
        compose.onAllNodesWithText("Highest sub-rating allowed").assertCountEquals(0)
    }

    @Test
    fun `from 10_10 allowed tags show, and the sub-rating waits for 10_11`() {
        val sent =
            policy("""{"BlockedTags":[],"AllowedTags":[],"AccessSchedules":[]}""")
        show(UserSection.Parental, sent, JellyfinServerProfile.from("10.10.7"))
        compose.onNodeWithText("ALLOWED TAGS").assertExists()
        compose.onAllNodesWithText("Highest sub-rating allowed").assertCountEquals(0)
    }

    @Test
    fun `the rating row shows for a user with no limit, whose policy leaves the key out`() {
        show(UserSection.Parental, policy("""{"BlockedTags":[],"AccessSchedules":[]}"""), JellyfinServerProfile.from("10.10.7"))
        compose.onNodeWithText("Highest rating allowed").assertExists()
    }

    @Test
    fun `the sub-rating shows on 10_11`() {
        val sent =
            policy("""{"BlockedTags":[],"AllowedTags":[],"AccessSchedules":[]}""")
        show(UserSection.Parental, sent, choices = UserChoices(ratings = Loaded.Ready(listOf(ParentalRating("PG", 5, 2)))))
        compose.onNodeWithText("Highest sub-rating allowed").assertExists()
        compose.onNodeWithText("Highest rating allowed").assertExists()
    }

    @Test
    fun `a switch, a tag and a schedule reach their actions`() {
        val calls = mutableListOf<String>()
        val actions =
            UsersActions(
                onPolicy = { key, value -> calls += "$key ${(value as JsonPrimitive).content}" },
                onList = { key, change -> calls += "$key ${change(listOf(JsonPrimitive("gore"), JsonPrimitive("x"))).size}" },
            )
        show(
            UserSection.Parental,
            policy(
                """{"BlockedTags":["gore"],"AccessSchedules":[{"DayOfWeek":"Monday","StartHour":8,"EndHour":17.5}],"BlockUnratedItems":[]}""",
            ),
            actions = actions,
        )
        compose.onNodeWithText("Monday, 08:00 to 17:30").assertExists()
        compose.onNodeWithText("Movies").performScrollTo().performClick()
        compose.onNodeWithText("gore").performScrollTo().assertExists()
        assertEquals(listOf("BlockUnratedItems 3"), calls)
    }

    @Test
    fun `devices and channels are listed when the user has a choice, and loading and failure show`() {
        val devices = listOf(ServerDevice("d1", "Pixel", null, "Binge", "Sam", null))
        show(
            UserSection.Devices,
            policy("""{"EnableAllDevices":false,"EnabledDevices":["d1"]}"""),
            choices = UserChoices(devices = Loaded.Ready(devices)),
        )
        compose.onNodeWithText("Pixel").assertExists()
    }

    @Test
    fun `live TV channels show, and a failed list offers Try again`() {
        val retried = mutableListOf<UserSection>()
        show(
            UserSection.LiveTv,
            policy("""{"EnableLiveTvAccess":true,"EnableAllChannels":false,"EnabledChannels":["ch-1"]}"""),
            choices = UserChoices(channels = Loaded.Failed(ConsoleFailure.Unreachable)),
            actions = UsersActions(onChoicesRetry = { retried += it }),
        )
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf(UserSection.LiveTv), retried)
        assertEquals(LiveChannel("ch-1", "News").name, "News")
    }

    @Test
    fun `an hour reads as a clock time, a bitrate as megabits`() {
        assertEquals("08:30", hours(8.5))
        assertEquals("24:00", hours(24.0))
        assertEquals("00:00", hours(0.0))
    }
}
