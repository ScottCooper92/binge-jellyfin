package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
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

/** When the home hints at a first library, and where its button goes. */
@RunWith(RobolectricTestRunner::class)
class FirstLibraryCardTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val session =
        JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private fun state(
        admin: Boolean,
        libraries: Int?,
    ) = UiState.SignedIn(
        session,
        titles = null,
        health = ConnectionHealth.Healthy,
        account = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":$admin}""").jsonObject),
        card = ServerCardState(libraries = libraries),
    )

    /** The home is a lazy list, so a row below the fold is scrolled to before it is looked for or found missing. */
    private fun scrollTo(text: String) = compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    private fun show(
        state: UiState.SignedIn,
        actions: Actions = Actions(),
    ) = compose.setContent { JellyfinTheme { JellyfinRoot(state, actions) } }

    @Test
    fun `an administrator of a server with no libraries gets the hint, and its button opens Libraries`() {
        val opened = mutableListOf<ConsolePage>()
        show(state(admin = true, libraries = 0), Actions(onOpenPage = { opened += it }))
        scrollTo("This server has no libraries yet")
        compose.onNodeWithText("This server has no libraries yet").assertExists()
        scrollTo("Add library")
        compose.onNodeWithText("Add library").assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(listOf(ConsolePage.Libraries), opened)
    }

    @Test
    fun `no hint once a library exists`() {
        show(state(admin = true, libraries = 1))
        scrollTo("Libraries")
        compose.onAllNodesWithText("This server has no libraries yet").assertCountEquals(0)
    }

    @Test
    fun `no hint while the count is unread or when its read failed`() {
        show(state(admin = true, libraries = null))
        scrollTo("Libraries")
        compose.onAllNodesWithText("This server has no libraries yet").assertCountEquals(0)
    }

    @Test
    fun `no hint for a user who cannot add a library`() {
        show(state(admin = false, libraries = 0))
        scrollTo("Sign out")
        compose.onAllNodesWithText("This server has no libraries yet").assertCountEquals(0)
    }

    @Test
    fun `the hint names no Binge`() {
        show(state(admin = true, libraries = 0))
        scrollTo("This server has no libraries yet")
        compose.onAllNodesWithText("Binge", substring = true).assertCountEquals(0)
    }
}
