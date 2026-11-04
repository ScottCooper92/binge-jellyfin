package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The users page given two panes' width: the list and the open user's page side by side. */
@RunWith(RobolectricTestRunner::class)
class UsersSplitTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private fun user(
        id: String,
        name: String,
    ) = ServerUser(id, name, hasPassword = true, lastSeenMillis = null, policy = JsonObject(emptyMap()))

    private val users = listOf(user("me", "claude-admin"), user("a", "Sam"), user("b", "Jo"))
    private val closed = mutableListOf<UserSection?>()

    private fun show(
        state: UsersUiState.Ready,
        split: Boolean,
    ) = compose.setContent {
        JellyfinTheme {
            // As the console's host provides them: a page given two panes has the home beside it, so no Back of its own.
            CompositionLocalProvider(LocalPageSpansTwoPanes provides split, LocalIsSinglePaneNav provides !split) {
                UsersScreen(state, UsersActions(onSection = { closed += it }))
            }
        }
    }

    private fun ready(
        open: String? = null,
        section: UserSection? = null,
    ) = UsersUiState.Ready(users, emptyList(), me = "me", open = open, section = section)

    @Test
    fun `beside the list the open user's page shows under the page's own title, their row marked`() {
        show(ready(open = "a"), split = true)
        compose.onNodeWithText("Users").assertIsDisplayed()
        compose.onNodeWithText("Jo").assertIsDisplayed()
        compose.onAllNodesWithText("Sam").assertCountEquals(2)
        compose.onAllNodes(hasText("Sam") and isSelected()).assertCountEquals(1)
        compose.onNodeWithText("Can sign in").assertIsDisplayed()
    }

    @Test
    fun `beside the list the add button sits under the list, whether or not a user is open`() {
        show(ready(open = "a"), split = true)
        compose.onNodeWithText("Add a user").assertIsDisplayed()
    }

    @Test
    fun `beside the list with nothing open the detail says to pick a user`() {
        show(ready(), split = true)
        compose.onNodeWithText("Jo").assertIsDisplayed()
        compose.onNodeWithText("Select a user to see their access and settings.").assertIsDisplayed()
    }

    @Test
    fun `a section opened beside the list has a Back of its own to the user's page`() {
        show(ready(open = "a", section = UserSection.Playback), split = true)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf<UserSection?>(null), closed)
    }

    @Test
    fun `on its own an open user replaces the list, titled with their name`() {
        show(ready(open = "a"), split = false)
        compose.onNodeWithText("Sam").assertIsDisplayed()
        compose.onAllNodesWithText("Jo").assertCountEquals(0)
        compose.onAllNodesWithText("Select a user to see their access and settings.").assertCountEquals(0)
    }
}
