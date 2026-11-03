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
    fun `tag delimiters are stored one character to an element, and read back as they were typed`() {
        assertEquals(names("/", "|", ";", "\\"), delimiterList("/|;\\"))
        assertEquals("/|;\\", options("""{"CustomTagDelimiters":["/","|",";","\\"]}""").tagDelimiters())
        // A character outside the basic plane is one element, not two halves.
        assertEquals(names("a", "\uD83D\uDE00"), delimiterList("a\uD83D\uDE00"))
        assertEquals(names(), delimiterList(""))
    }

    @Test
    fun `the delimiter whitelist is one name a line, trimmed, with no blank line`() {
        assertEquals(names("AC/DC", "Back in Black"), whitelistList("AC/DC\n  \n Back in Black "))
        assertEquals(names(), whitelistList("\n"))
        assertEquals(listOf("AC/DC"), options("""{"DelimiterWhitelist":["AC/DC"," "]}""").delimiterWhitelist())
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

    private val movie =
        FetcherType(
            "Movie",
            metadata = listOf(AvailableFetcher("TheMovieDb", true), AvailableFetcher("OMDb", false)),
            images = listOf(AvailableFetcher("TheMovieDb", true), AvailableFetcher("Screen Grabber", true)),
        )

    @Test
    fun `a library with no entry for a type has each fetcher as the server offers it, in the server's order`() {
        val library = options("""{"TypeOptions":[{"Type":"Series","MetadataFetchers":[]}]}""")
        assertEquals(listOf(Provider("TheMovieDb", true), Provider("OMDb", false)), library.fetchers(movie, FetcherKind.Metadata))
        assertEquals(listOf(Provider("TheMovieDb", true), Provider("Screen Grabber", true)), library.fetchers(movie, FetcherKind.Images))
    }

    @Test
    fun `an entry that chose says which are on and the order, and a kind it never chose keeps the server's defaults`() {
        val library =
            options(
                """{"TypeOptions":[{"Type":"movie","MetadataFetchers":["OMDb"],"MetadataFetcherOrder":["OMDb","Gone","TheMovieDb"]}]}""",
            )
        assertEquals(listOf(Provider("OMDb", true), Provider("TheMovieDb", false)), library.fetchers(movie, FetcherKind.Metadata))
        // Its image lists were never written, so the server's defaults hold for them.
        assertEquals(listOf(Provider("TheMovieDb", true), Provider("Screen Grabber", true)), library.fetchers(movie, FetcherKind.Images))
    }

    @Test
    fun `the first change to a type writes its whole entry, both kinds, and leaves the other types alone`() {
        val library =
            options("""{"Other":1,"TypeOptions":[{"Type":"Series","MetadataFetchers":["TheTVDB"],"MetadataFetcherOrder":["TheTVDB"]}]}""")
        val written =
            library.withFetchers(movie, FetcherKind.Metadata) { fetchers ->
                fetchers.map { if (it.name == "OMDb") it.copy(enabled = true) else it }
            }
        assertEquals(2, written.size)
        // Series, which was not touched, is as it was.
        assertEquals(library.getValue("TypeOptions").let { (it as JsonArray)[0] }, written[0])
        val entry = written[1].jsonObject
        assertEquals(JsonPrimitive("Movie"), entry["Type"])
        assertEquals(names("TheMovieDb", "OMDb"), entry["MetadataFetchers"])
        assertEquals(names("TheMovieDb", "OMDb"), entry["MetadataFetcherOrder"])
        // The images side is written out too, as the server offered it: an entry that exists means the library has chosen.
        assertEquals(names("TheMovieDb", "Screen Grabber"), entry["ImageFetchers"])
        assertEquals(names("TheMovieDb", "Screen Grabber"), entry["ImageFetcherOrder"])
    }

    @Test
    fun `a change to an entry keeps its other keys, such as its image options, and its place in the array`() {
        val library =
            options(
                """{"TypeOptions":[{"Type":"Movie","MetadataFetchers":["TheMovieDb"],"MetadataFetcherOrder":["TheMovieDb","OMDb"],""" +
                    """"ImageFetchers":["TheMovieDb"],"ImageFetcherOrder":["TheMovieDb","Screen Grabber"],""" +
                    """"ImageOptions":[{"Type":"Backdrop","Limit":2,"MinWidth":1280}],"Future":7},{"Type":"Series"}]}""",
            )
        val written = library.withFetchers(movie, FetcherKind.Images) { it.reversed() }
        val entry = written[0].jsonObject
        assertEquals(names("Screen Grabber", "TheMovieDb"), entry["ImageFetcherOrder"])
        // Reordering does not switch anything: Screen Grabber was off in this entry and stays off.
        assertEquals(names("TheMovieDb"), entry["ImageFetchers"])
        assertEquals(library.getValue("TypeOptions").let { (it as JsonArray)[0].jsonObject["ImageOptions"] }, entry["ImageOptions"])
        assertEquals(JsonPrimitive(7), entry["Future"])
        assertEquals(JsonPrimitive("Series"), written[1].jsonObject["Type"])
        // The metadata side is as the entry had it.
        assertEquals(names("TheMovieDb"), entry["MetadataFetchers"])
        assertEquals(names("TheMovieDb", "OMDb"), entry["MetadataFetcherOrder"])
    }

    private fun names(vararg names: String) = JsonArray(names.map(::JsonPrimitive))
}
