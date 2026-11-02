package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Where a user's own settings live: behind the avatar for an administrator, inline on the home for everyone else. */
@RunWith(RobolectricTestRunner::class)
class HomeAccountTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val session =
        JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private fun state(admin: Boolean) =
        UiState.SignedIn(
            session,
            titles = null,
            account = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":$admin}""").jsonObject),
        )

    @Test
    fun `an administrator's home has the avatar, the server and Administration, and none of the account's rows`() {
        compose.setContent { JellyfinTheme { JellyfinRoot(state(admin = true), Actions()) } }
        compose.onNodeWithContentDescription("Your account").assertExists()
        compose.onNodeWithText("Libraries").performScrollTo().assertExists()
        compose.onAllNodesWithText("Approve a sign-in code").assertCountEquals(0)
        compose.onAllNodesWithText("Change password").assertCountEquals(0)
        compose.onAllNodesWithText("Sign out").assertCountEquals(0)
    }

    @Test
    fun `the avatar opens the account`() {
        val opened = mutableListOf<ConsolePage>()
        compose.setContent { JellyfinTheme { JellyfinRoot(state(admin = true), Actions(onOpenPage = { opened += it })) } }
        compose.onNodeWithContentDescription("Your account").performClick()
        assertEquals(listOf(ConsolePage.Account), opened)
    }

    @Test
    fun `anyone else has the account's rows inline, Sign out last, and no avatar`() {
        val asked = mutableListOf<String>()
        compose.setContent {
            JellyfinTheme { JellyfinRoot(state(admin = false), Actions(onAskSignOut = { asked += "ask" })) }
        }
        compose.onAllNodesWithContentDescription("Your account").assertCountEquals(0)
        compose.onNodeWithText("Approve a sign-in code").assertExists()
        compose.onNodeWithText("Playback").assertExists()
        compose.onNodeWithText("Home screen").assertExists()
        compose.onAllNodesWithText("Libraries").assertCountEquals(0)
        // The list is lazy: Sign out is its last item, so it is scrolled to, not just found.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(listOf("ask"), asked)
    }

    @Test
    fun `the account page has the same rows, and Sign out asks first`() {
        val calls = mutableListOf<String>()
        val signedIn = state(admin = true)
        compose.setContent {
            JellyfinTheme {
                AccountScreen(signedIn, Actions(onAskSignOut = { calls += "ask" }, onOpenPage = { calls += it.name }), onBack = {})
            }
        }
        compose.onNodeWithText("Your account").assertExists()
        compose.onNodeWithText("Playback").performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(listOf("Playback", "ask"), calls)
    }

    @Test
    fun `the account page shows the sign-out confirm when it is asked`() {
        compose.setContent { JellyfinTheme { AccountScreen(state(admin = true).copy(confirmingSignOut = true), Actions(), onBack = {}) } }
        compose.onNodeWithText("Sign out of Jellyfin?").assertExists()
    }
}
