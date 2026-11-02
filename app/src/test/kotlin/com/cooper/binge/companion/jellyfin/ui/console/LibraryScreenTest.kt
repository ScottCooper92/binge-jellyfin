package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cooper.binge.companion.jellyfin.server.AvailableProviders
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.MetadataSaver
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which of a library's options a kind of library, a release and what the server sent show, and that a tap reaches its action. */
@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val savers = AvailableProviders(metadataSavers = listOf(MetadataSaver("Nfo", false)))

    private fun show(
        kind: String,
        options: String,
        version: String = "10.11.6",
        available: AvailableProviders = savers,
        actions: LibraryActions = LibraryActions(),
    ) {
        val json = Json.parseToJsonElement(options).jsonObject
        val state =
            LibraryUiState.Ready(
                library = ServerLibrary("id-$kind", "Library", kind, listOf("/media"), json),
                options = json,
                available = available,
            )
        compose.setContent { JellyfinTheme { LibraryScreen(state, actions, JellyfinServerProfile.from(version)) } }
    }

    @Test
    fun `a video library shows which embedded subtitles it offers, and the choice it has`() {
        show("movies", """{"AllowEmbeddedSubtitles":"AllowText"}""")
        compose.onNodeWithText("Embedded subtitles").performScrollTo().assertExists()
        compose.onNodeWithText("Text only").assertExists()
    }

    @Test
    fun `the choice is not offered for home videos`() {
        show("homevideos", """{"AllowEmbeddedSubtitles":"AllowAll"}""")
        compose.onAllNodesWithText("Embedded subtitles").assertCountEquals(0)
    }

    @Test
    fun `a server that never sent the choice is not offered it`() {
        show("movies", """{"Enabled":true}""")
        compose.onAllNodesWithText("Embedded subtitles").assertCountEquals(0)
    }

    @Test
    fun `picking an embedded subtitle type sends the server's name for it`() {
        val calls = mutableListOf<String>()
        show(
            "movies",
            """{"AllowEmbeddedSubtitles":"AllowAll"}""",
            actions = LibraryActions(onText = { key, text -> calls += "$key $text" }),
        )
        compose.onNodeWithText("Embedded subtitles").performScrollTo().performClick()
        compose.onNodeWithText("Image only").performClick()
        assertEquals(listOf("AllowEmbeddedSubtitles AllowImage"), calls)
    }

    @Test
    fun `embedded titles for extras need embedded titles on, and a release that has them`() {
        show("movies", """{"EnableEmbeddedTitles":true,"EnableEmbeddedExtrasTitles":false}""")
        compose.onNodeWithText("Embedded titles for extras").performScrollTo().assertExists()
    }

    @Test
    fun `embedded titles for extras are not shown while embedded titles are off`() {
        show("movies", """{"EnableEmbeddedTitles":false,"EnableEmbeddedExtrasTitles":false}""")
        compose.onAllNodesWithText("Embedded titles for extras").assertCountEquals(0)
    }

    @Test
    fun `embedded titles for extras are not on 10_8`() {
        show("movies", """{"EnableEmbeddedTitles":true,"EnableEmbeddedExtrasTitles":false}""", version = "10.8.13")
        compose.onAllNodesWithText("Embedded titles for extras").assertCountEquals(0)
    }

    @Test
    fun `a metadata saver the server offers has a switch that reaches its action`() {
        val calls = mutableListOf<String>()
        show("movies", """{"MetadataSavers":["Old plugin"]}""", actions = LibraryActions(onJson = { key, value -> calls += "$key $value" }))
        compose.onNodeWithText("Nfo").performScrollTo().performClick()
        // The whole list goes, with the name the server no longer lists kept.
        assertEquals(listOf("""MetadataSavers ["Old plugin","Nfo"]"""), calls)
    }

    @Test
    fun `no saver is shown when the server offers none`() {
        show("movies", """{"MetadataSavers":[]}""", available = AvailableProviders())
        compose.onAllNodesWithText("Nfo").assertCountEquals(0)
    }
}
