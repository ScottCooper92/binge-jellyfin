package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The release notes address, built from the version the server reports and nothing else, and the rows that open it. */
@RunWith(RobolectricTestRunner::class)
class ReleaseNotesTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val releases = "https://github.com/jellyfin/jellyfin/releases"

    @Test
    fun `a release version opens its own tag page, with the v Jellyfin tags it with`() {
        assertEquals("$releases/tag/v10.11.6", releaseNotesUrl("10.11.6"))
        assertEquals("$releases/tag/v10.10.7", releaseNotesUrl("10.10.7"))
        assertEquals("$releases/tag/v12.4", releaseNotesUrl("12.4"))
        assertEquals("$releases/tag/v12.4.0", releaseNotesUrl("12.4.0"))
        assertEquals("$releases/tag/v10.11.6", releaseNotesUrl("  10.11.6 "))
    }

    @Test
    fun `anything that is not a plain version opens the list of releases`() {
        listOf(
            "10.12.0-unstable",
            "10.12.0+abc123",
            "10.11.6.1",
            "10",
            "v10.11.6",
            "",
            "   ",
            "latest",
            "10.11.6/../x",
            "10.11?x=1",
            "10.11.6#frag",
        ).forEach { assertEquals(it, releases, releaseNotesUrl(it)) }
        assertEquals(releases, releaseNotesUrl(null))
    }

    private fun show(
        version: String,
        canOpen: (String) -> Boolean = { true },
        opened: MutableList<String> = mutableListOf(),
    ) = compose.setContent {
        JellyfinTheme {
            ItemGroup(title = null, rows = releaseNotesItems(version, canOpen = canOpen, open = { opened += it }))
        }
    }

    @Test
    fun `the Release notes row names the running version and opens its page`() {
        val opened = mutableListOf<String>()
        show("10.11.6", opened = opened)
        compose.onNodeWithText("Release notes").assertIsDisplayed()
        compose.onNodeWithText("10.11.6").assertIsDisplayed()
        compose.onNodeWithText("Release notes").performClick()
        assertEquals(listOf("$releases/tag/v10.11.6"), opened)
    }

    @Test
    fun `a build with no release tag opens the list of releases`() {
        val opened = mutableListOf<String>()
        show("10.12.0-unstable", opened = opened)
        compose.onNodeWithText("Release notes").performClick()
        assertEquals(listOf(releases), opened)
    }

    private val session = JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private fun showCard(
        update: Boolean,
        canOpen: (String) -> Boolean = { true },
        opened: MutableList<String> = mutableListOf(),
    ) = compose.setContent {
        JellyfinTheme {
            val state = UiState.SignedIn(session, card = ServerCardState(updateAvailable = update))
            ServerHomeCard(state, canOpen = canOpen, open = { opened += it })
        }
    }

    @Test
    fun `the Update available pill on the server card opens the list of releases`() {
        val opened = mutableListOf<String>()
        showCard(update = true, opened = opened)
        // Read as a button that says where it goes; a pill with nothing to tap is read by its label.
        compose.onNode(hasClickAction() and hasContentDescription("Release notes")).assertExists()
        compose.onNodeWithContentDescription("Release notes").performClick()
        assertEquals(listOf(releases), opened)
    }

    @Test
    fun `where nothing can open it the pill is a label, with nothing to tap`() {
        showCard(update = true, canOpen = { false })
        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNode(hasClickAction() and hasText("Update available")).assertDoesNotExist()
        compose.onNodeWithContentDescription("Release notes").assertDoesNotExist()
    }

    @Test
    fun `a server with no update has no pill`() {
        showCard(update = false)
        compose.onNodeWithText("Update available").assertDoesNotExist()
    }

    @Test
    fun `where no browser can open it, no row is offered`() {
        show("10.11.6", canOpen = { false })
        compose.onNodeWithText("Release notes").assertDoesNotExist()
    }

    @Test
    fun `no version means no row`() {
        show("")
        compose.onNodeWithText("Release notes").assertDoesNotExist()
    }
}
