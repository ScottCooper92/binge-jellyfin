package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinLibraryOptionsTest {
    private val available = listOf("Open Subtitles", "Podnapisi", "Other")

    private fun options(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `providers come in the library's order, then the rest in the server's, and a disabled one is off`() {
        val library = options("""{"SubtitleFetcherOrder":["podnapisi","Gone"],"DisabledSubtitleFetchers":["Other"]}""")
        assertEquals(
            listOf(Provider("Podnapisi", true), Provider("Open Subtitles", true), Provider("Other", false)),
            library.providers(ProviderGroup.Subtitles, available),
        )
    }

    @Test
    fun `a library with no order or disabled list uses every provider in the server's order`() {
        assertEquals(
            available.map { Provider(it, true) },
            JsonObject(emptyMap()).providers(ProviderGroup.Subtitles, available),
        )
    }

    @Test
    fun `moving a provider writes the whole order, and stops at the ends`() {
        val library = options("""{"SubtitleFetcherOrder":["Podnapisi"]}""")

        fun order(
            name: String,
            by: Int,
        ) = library.providerOrder(ProviderGroup.Subtitles, available, name, by)
        assertEquals(names("Open Subtitles", "Podnapisi", "Other"), order("Podnapisi", 1))
        assertEquals(names("Podnapisi", "Other", "Open Subtitles"), order("Open Subtitles", 1))
        assertEquals(names("Podnapisi", "Open Subtitles", "Other"), order("Podnapisi", -1))
        assertEquals(names("Podnapisi", "Open Subtitles", "Other"), order("Other", 5))
        assertEquals(names("Podnapisi", "Open Subtitles", "Other"), order("Missing", 1))
    }

    @Test
    fun `switching a provider off adds it to the disabled list, and on takes it out, keeping names it doesn't list`() {
        val library = options("""{"DisabledSubtitleFetchers":["Old plugin"]}""")
        assertEquals(names("Old plugin", "Podnapisi"), library.providersDisabled(ProviderGroup.Subtitles, "Podnapisi", enabled = false))
        assertEquals(names(), library.providersDisabled(ProviderGroup.Subtitles, "old plugin", enabled = true))
        assertEquals(names("Old plugin"), library.providersDisabled(ProviderGroup.Subtitles, "Podnapisi", enabled = true))
    }

    @Test
    fun `a library that never chose has the savers the server turns on, and a change writes the whole list`() {
        val savers = listOf(MetadataSaver("Nfo", true), MetadataSaver("Other", false))
        val fresh = options("{}")
        assertEquals(listOf("Nfo"), fresh.enabledSavers(savers))
        assertEquals(names("Nfo", "Other"), fresh.saversWith(savers, "Other", enabled = true))
        assertEquals(names(), fresh.saversWith(savers, "nfo", enabled = false))
        // A library that has chosen keeps its own list, an empty one included, and a name the server doesn't list.
        assertEquals(emptyList<String>(), options("""{"MetadataSavers":[]}""").enabledSavers(savers))
        val chosen = options("""{"MetadataSavers":["Old plugin"]}""")
        assertEquals(listOf("Old plugin"), chosen.enabledSavers(savers))
        assertEquals(names("Old plugin", "Nfo"), chosen.saversWith(savers, "Nfo", enabled = true))
        assertEquals(names("Old plugin"), chosen.saversWith(savers, "Nfo", enabled = false))
    }

    @Test
    fun `a download language switches on and off by its three-letter code, in lower case, keeping codes the server doesn't list`() {
        val library = options("""{"SubtitleDownloadLanguages":["eng","XYZ"]}""")
        assertEquals(listOf("eng", "XYZ"), library.downloadLanguages())
        assertEquals(names("eng", "XYZ", "fre"), library.languagesWith("FRE", on = true))
        assertEquals(names("XYZ"), library.languagesWith("ENG", on = false))
        // A library the server has never given a list leaves the key null, which reads as none.
        assertEquals(emptyList<String>(), options("""{"SubtitleDownloadLanguages":null}""").downloadLanguages())
        assertEquals(names("spa"), options("{}").languagesWith("spa", on = true))
    }

    @Test
    fun `the embedded subtitle choice is for the kinds of video library jellyfin-web shows it for`() {
        assertTrue(libraryHasEmbeddedSubtitleChoice("movies"))
        assertTrue(libraryHasEmbeddedSubtitleChoice("tvshows"))
        assertTrue(libraryHasEmbeddedSubtitleChoice("musicvideos"))
        assertTrue(libraryHasEmbeddedSubtitleChoice(MIXED_LIBRARY))
        assertFalse(libraryHasEmbeddedSubtitleChoice("homevideos"))
        assertFalse(libraryHasEmbeddedSubtitleChoice("music"))
    }

    @Test
    fun `which options a kind of library has`() {
        assertTrue(libraryHasVideo("movies"))
        assertTrue(libraryHasVideo(MIXED_LIBRARY))
        assertFalse(libraryHasVideo("music"))
        assertFalse(libraryHasVideo("books"))
        assertTrue(libraryHasShows("tvshows"))
        assertFalse(libraryHasShows("movies"))
        assertTrue(libraryHasMovies("movies"))
        assertTrue(libraryHasMovies(MIXED_LIBRARY))
        assertFalse(libraryHasMovies("tvshows"))
    }

    private fun names(vararg names: String) = JsonArray(names.map(::JsonPrimitive))
}
