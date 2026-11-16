package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
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
        update: Boolean = false,
        canOpen: (String) -> Boolean = { true },
        opened: MutableList<String> = mutableListOf(),
    ) = compose.setContent {
        JellyfinTheme {
            ItemGroup(title = null, rows = releaseNotesItems(version, update, canOpen = canOpen, open = { opened += it }))
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

    @Test
    fun `Update available shows only when the server says so, and opens the list of releases`() {
        val opened = mutableListOf<String>()
        show("10.11.6", update = true, opened = opened)
        compose.onNodeWithText("Update available").performClick()
        assertEquals(listOf(releases), opened)
    }

    @Test
    fun `without an update there is no update row`() {
        show("10.11.6", update = false)
        compose.onNodeWithText("Update available").assertDoesNotExist()
    }

    @Test
    fun `no version means no row to name it, though an update is still offered`() {
        show("", update = true)
        compose.onNodeWithText("Release notes").assertDoesNotExist()
        compose.onNodeWithText("Update available").assertIsDisplayed()
    }

    @Test
    fun `where no browser can open it, no row is offered`() {
        show("10.11.6", update = true, canOpen = { false })
        compose.onNodeWithText("Release notes").assertDoesNotExist()
        compose.onNodeWithText("Update available").assertDoesNotExist()
    }
}
