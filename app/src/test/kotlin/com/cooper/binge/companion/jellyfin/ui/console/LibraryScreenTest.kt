package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.AvailableProviders
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.FetcherKind
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.MetadataSaver
import com.cooper.binge.companion.jellyfin.server.ProviderGroup
import com.cooper.binge.companion.jellyfin.server.ServerLibrary
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Which of a library's options a kind of library, a release and what the server sent show, and that a tap reaches its action. */
@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val savers = AvailableProviders(metadataSavers = listOf(MetadataSaver("Nfo", false)))

    private fun show(
        kind: String,
        options: String,
        version: String = "10.11.6",
        available: AvailableProviders = savers,
        actions: LibraryActions = LibraryActions(),
        dialog: LibraryDialog? = null,
        cultures: List<Culture> = emptyList(),
        page: LibraryPage = LibraryPage.Main,
    ) {
        val json = Json.parseToJsonElement(options).jsonObject
        val state =
            LibraryUiState.Ready(
                library = ServerLibrary("id-$kind", "Library", kind, listOf("/media"), json),
                options = json,
                available = available,
                dialog = dialog,
                cultures = cultures,
            )
        compose.setContent { JellyfinTheme { LibraryScreen(state, actions, JellyfinServerProfile.from(version), page = page) } }
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

    private val tagOptions =
        """{"PreferNonstandardArtistsTag":false,"UseCustomTagDelimiters":true,"CustomTagDelimiters":["/","|"],"DelimiterWhitelist":["AC/DC"]}"""

    @Test
    fun `a music library shows its audio tag options from 10_10, and what they say`() {
        show("music", tagOptions)
        compose.onNodeWithText("Prefer the ARTISTS tag").performScrollTo().assertExists()
        compose.onNodeWithText("Custom tag delimiters").assertExists()
        compose.onNodeWithText("/|").assertExists()
        compose.onNodeWithText("1 name").assertExists()
    }

    @Test
    fun `the delimiter characters are only shown while custom delimiters are on`() {
        show("music", tagOptions.replace("\"UseCustomTagDelimiters\":true", "\"UseCustomTagDelimiters\":false"))
        compose.onNodeWithText("Custom tag delimiters").performScrollTo().assertExists()
        compose.onAllNodesWithText("Delimiter characters").assertCountEquals(0)
        compose.onNodeWithText("Delimiter whitelist").assertExists()
    }

    @Test
    fun `the audio tag options are not on 10_9`() {
        show("music", tagOptions, version = "10.9.0")
        compose.onAllNodesWithText("Prefer the ARTISTS tag").assertCountEquals(0)
    }

    @Test
    fun `a movies library has no audio tag options`() {
        show("movies", tagOptions)
        compose.onAllNodesWithText("Prefer the ARTISTS tag").assertCountEquals(0)
    }

    @Test
    fun `a switch for an audio tag option reaches its action`() {
        val calls = mutableListOf<String>()
        show("music", tagOptions, actions = LibraryActions(onFlag = { key, on -> calls += "$key $on" }))
        compose.onNodeWithText("Prefer the ARTISTS tag").performScrollTo().performClick()
        assertEquals(listOf("PreferNonstandardArtistsTag true"), calls)
    }

    @Test
    fun `typed delimiter characters are sent one to an element, and the sheet closes`() {
        val calls = mutableListOf<String>()
        show(
            "music",
            tagOptions,
            actions = LibraryActions(onJson = { key, value -> calls += "$key $value" }, onAsk = { calls += "ask $it" }),
            dialog = LibraryDialog.TagDelimiters,
        )
        compose.onNode(hasSetTextAction()).performTextReplacement("&;")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("""CustomTagDelimiters ["&",";"]""", "ask null"), calls)
    }

    @Test
    fun `the whitelist is sent one name to a line, with blank lines dropped`() {
        val calls = mutableListOf<String>()
        show(
            "music",
            tagOptions,
            actions = LibraryActions(onJson = { key, value -> calls += "$key $value" }, onAsk = { calls += "ask $it" }),
            dialog = LibraryDialog.DelimiterWhitelist,
        )
        compose.onNode(hasSetTextAction()).performTextReplacement("AC/DC\n\nBack in Black")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("""DelimiterWhitelist ["AC/DC","Back in Black"]""", "ask null"), calls)
    }

    private val languages =
        listOf(
            Culture("English", "en", listOf("eng")),
            Culture("French", "fr", listOf("fre", "fra")),
            Culture("Spanish", "es", listOf("spa")),
        )

    @Test
    fun `the download languages row says which languages by name when there are few`() {
        show("movies", """{"SubtitleDownloadLanguages":["eng","fre"]}""", cultures = languages)
        compose.onNodeWithText("Download languages").performScrollTo().assertExists()
        compose.onNodeWithText("English, French").assertExists()
    }

    @Test
    fun `the download languages row says how many when there are more than two`() {
        show("movies", """{"SubtitleDownloadLanguages":["eng","fre","spa"]}""", cultures = languages)
        compose.onNodeWithText("3 languages").performScrollTo().assertExists()
    }

    @Test
    fun `no download languages reads as none`() {
        show("movies", """{"SubtitleDownloadLanguages":null}""", cultures = languages)
        compose.onNodeWithText("Download languages").performScrollTo().assertExists()
        compose.onNodeWithText("None").assertExists()
    }

    @Test
    fun `a library that never chose download languages still offers them, as none`() {
        show("movies", """{"Enabled":true}""", cultures = languages)
        compose.onNodeWithText("Download languages").performScrollTo().assertExists()
        compose.onNodeWithText("None").assertExists()
    }

    @Test
    fun `a tap on a language adds the key when the library has none`() {
        val calls = mutableListOf<String>()
        show(
            "movies",
            """{"Enabled":true}""",
            actions = LibraryActions(onJson = { key, value -> calls += "$key $value" }),
            cultures = languages,
            page = LibraryPage.Languages,
        )
        // The page is a lazy list, so a language below the first screen is scrolled to before it is tapped.
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("Spanish"))
        compose.onNodeWithText("Spanish").performClick()
        assertEquals(listOf("""SubtitleDownloadLanguages ["spa"]"""), calls)
    }

    @Test
    fun `the languages page has a switch for each language the server lists, and a tap sends the whole list`() {
        val calls = mutableListOf<String>()
        show(
            "movies",
            """{"SubtitleDownloadLanguages":["eng","XYZ"]}""",
            actions = LibraryActions(onJson = { key, value -> calls += "$key $value" }),
            cultures = languages,
            page = LibraryPage.Languages,
        )
        compose.onNodeWithText("French").performClick()
        compose.onNodeWithText("English").performClick()
        // Each tap is made on the list the page shows, so the second does not carry the first.
        assertEquals(listOf("""SubtitleDownloadLanguages ["eng","XYZ","fre"]""", """SubtitleDownloadLanguages ["XYZ"]"""), calls)
    }

    @Test
    fun `the languages page lists the languages under the first letter of their names`() {
        show("movies", """{"SubtitleDownloadLanguages":[]}""", cultures = languages, page = LibraryPage.Languages)
        compose.onNodeWithText("E").assertExists()
        compose.onNodeWithText("F").assertExists()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("S"))
        compose.onNodeWithText("S").assertExists()
    }

    /** Forty-five languages under three letters, so the page is long enough for a rail and short enough to draw every letter. */
    private val manyLanguages =
        listOf("A", "M", "Z").flatMap { letter ->
            (1..15).map { Culture("${letter}lang%02d".format(it), "x", listOf("$letter%02d".format(it))) }
        }

    @Test
    fun `a long languages page has a letter rail that jumps to a letter`() {
        show("movies", """{"SubtitleDownloadLanguages":[]}""", cultures = manyLanguages, page = LibraryPage.Languages)
        // Before the jump only the rail's own Z is composed; the group's header and its languages are further down.
        compose.onAllNodesWithText("Z").assertCountEquals(1)
        compose.onNodeWithText("Zlang01").assertDoesNotExist()
        compose.onNodeWithText("Z").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Zlang01").assertExists()
    }

    @Test
    fun `a short languages page has no rail`() {
        show("movies", """{"SubtitleDownloadLanguages":[]}""", cultures = languages, page = LibraryPage.Languages)
        // Each letter is just its group's header.
        compose.onAllNodesWithText("E").assertCountEquals(1)
    }

    @Test
    fun `the rail drops the letters a search has emptied`() {
        show("movies", """{"SubtitleDownloadLanguages":[]}""", cultures = manyLanguages, page = LibraryPage.Languages)
        compose.onNode(hasSetTextAction()).performTextReplacement("Zlang")
        compose.onNodeWithText("A").assertDoesNotExist()
        compose.onNodeWithText("M").assertDoesNotExist()
    }

    @Test
    fun `the languages page says so when the server listed no languages`() {
        show("movies", """{"SubtitleDownloadLanguages":[]}""", page = LibraryPage.Languages)
        compose.onNodeWithText("The server did not list any languages.").assertExists()
    }

    @Test
    fun `language search matches trimmed names and removes empty letter headings`() {
        show("movies", """{"SubtitleDownloadLanguages":[]}""", cultures = languages, page = LibraryPage.Languages)
        compose.onNode(hasSetTextAction()).performTextReplacement(" fRe ")
        compose.onNodeWithText("French").assertExists()
        compose.onNodeWithText("F").assertExists()
        compose.onNodeWithText("E").assertDoesNotExist()
        compose.onNodeWithText("S").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextReplacement("Klingon")
        compose.onNodeWithText("No languages match that search").assertExists()
    }

    @Test
    fun `a language toggled on stays in its letter group and stays on when the search clears`() {
        val json = Json.parseToJsonElement("""{"SubtitleDownloadLanguages":[]}""").jsonObject
        var state by mutableStateOf(
            LibraryUiState.Ready(
                library = ServerLibrary("id", "Library", "movies", emptyList(), json),
                options = json,
                available = savers,
                cultures = languages,
            ),
        )
        val actions = LibraryActions(onJson = { key, value -> state = state.copy(options = JsonObject(state.options + (key to value))) })
        compose.setContent { JellyfinTheme { LanguagesPage(state, actions, PaddingValues()) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("Spanish")
        compose.onNode(hasText("Spanish") and hasSetTextAction().not()).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Selected", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("S").assertExists()
        compose.onNodeWithText("Spanish").assertIsOn()
        compose.onAllNodes(hasScrollAction()).onLast().performScrollToNode(hasText("English"))
        compose.onNodeWithText("English").assertExists()
    }

    @Test
    fun `no saver is shown when the server offers none`() {
        show("movies", """{"MetadataSavers":[]}""", available = AvailableProviders())
        compose.onAllNodesWithText("Nfo").assertCountEquals(0)
    }

    @Test
    fun `a page opened after scrolling the library starts with its bar shown, and the library keeps its place`() {
        val json = Json.parseToJsonElement("""{"AllowEmbeddedSubtitles":"AllowText","SubtitleDownloadLanguages":[]}""").jsonObject
        val state =
            LibraryUiState.Ready(
                library = ServerLibrary("id-movies", "Library", "movies", listOf("/media"), json),
                options = json,
                available = savers,
                cultures = languages,
            )
        var page by mutableStateOf<LibraryPage>(LibraryPage.Main)
        compose.setContent { JellyfinTheme { LibraryScreen(state, LibraryActions(), JellyfinServerProfile.from("10.11.6"), page = page) } }
        // Scrolling the form to its last row slides the single-line bar away.
        compose.onNodeWithText("Embedded subtitles").performScrollTo()
        page = LibraryPage.Languages
        compose.waitForIdle()
        compose.onNodeWithText("Download languages").assertIsDisplayed()
    }

    @Test
    fun `each of a library's pages has a key of its own`() {
        val pages =
            listOf(
                LibraryPage.Main,
                LibraryPage.Languages,
                LibraryPage.Providers(ProviderGroup.entries.first()),
                LibraryPage.One(ProviderGroup.entries.first(), "A"),
                LibraryPage.One(ProviderGroup.entries.first(), "B"),
                LibraryPage.Fetchers(FetcherKind.entries.first()),
                LibraryPage.Fetcher(FetcherKind.entries.first(), "Movie", "A"),
                LibraryPage.Fetcher(FetcherKind.entries.first(), "Movie", "B"),
            )
        assertEquals(pages.size, pages.map { it.positionKey }.toSet().size)
        assertNotEquals(LibraryPage.Main.positionKey, LibraryPage.Languages.positionKey)
    }
}
