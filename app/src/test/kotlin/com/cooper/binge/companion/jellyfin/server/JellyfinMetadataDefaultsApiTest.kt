package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The metadata defaults as the server's configuration holds them, and the one-list changes the page makes. */
class JellyfinMetadataDefaultsApiTest {
    private val configuration: JsonObject =
        Json
            .parseToJsonElement(
                """{"ServerName":"truenas","Future":{"keep":"me"},"MetadataOptions":[
                    {"ItemType":"Movie","DisabledMetadataFetchers":[],"DisabledImageFetchers":[]},
                    {"ItemType":"MusicVideo","DisabledMetadataFetchers":["The Open Movie Database"],"DisabledImageFetchers":["The Open Movie Database"],"ImageFetcherOrder":["a","b"]}
                ]}""",
            ).jsonObject

    @Test
    fun `the types are those the defaults hold an entry for, in the server's order`() {
        assertEquals(listOf("Movie", "MusicVideo"), configuration.metadataTypes())
    }

    @Test
    fun `a fetcher is on unless the entry switches it off, and a name the server stopped offering still shows`() {
        val fetchers = configuration.fetchers("MusicVideo", FetcherKind.Metadata, offered = listOf("TheMovieDb", "The Open Movie Database"))

        assertEquals(listOf(Provider("TheMovieDb", true), Provider("The Open Movie Database", false)), fetchers)
        assertEquals(
            listOf(Provider("TheMovieDb", true), Provider("Gone", false)),
            configuration
                .withFetcher(
                    "Movie",
                    FetcherKind.Metadata,
                    "Gone",
                    false,
                ).fetchers("Movie", FetcherKind.Metadata, listOf("TheMovieDb")),
        )
    }

    @Test
    fun `a type with no entry has everything on`() {
        assertEquals(listOf(Provider("TheTVDB", true)), configuration.fetchers("Series", FetcherKind.Images, listOf("TheTVDB")))
    }

    @Test
    fun `switching one off changes that list of that entry and nothing else`() {
        val changed = configuration.withFetcher("Movie", FetcherKind.Images, "TheMovieDb", enabled = false)

        assertEquals(JsonArray(listOf(JsonPrimitive("TheMovieDb"))), changed.entryOf("Movie").getValue("DisabledImageFetchers"))
        assertEquals(configuration.entryOf("MusicVideo"), changed.entryOf("MusicVideo"))
        assertEquals(
            configuration.entryOf("Movie").getValue("DisabledMetadataFetchers"),
            changed.entryOf("Movie").getValue("DisabledMetadataFetchers"),
        )
        assertEquals(configuration.getValue("Future"), changed.getValue("Future"))
        assertEquals(configuration.getValue("ServerName"), changed.getValue("ServerName"))
    }

    @Test
    fun `switching one back on removes it, and keeps what else the entry holds`() {
        val changed = configuration.withFetcher("MusicVideo", FetcherKind.Images, "The Open Movie Database", enabled = true)

        assertEquals(JsonArray(emptyList()), changed.entryOf("MusicVideo").getValue("DisabledImageFetchers"))
        assertEquals(
            configuration.entryOf("MusicVideo").getValue("ImageFetcherOrder"),
            changed.entryOf("MusicVideo").getValue("ImageFetcherOrder"),
        )
    }

    @Test
    fun `the first change to a type with no entry writes one with its item type and that list alone`() {
        val changed = configuration.withFetcher("Series", FetcherKind.Metadata, "TheTVDB", enabled = false)

        assertEquals(listOf("Movie", "MusicVideo", "Series"), changed.metadataTypes())
        assertEquals(
            JsonObject(
                mapOf(
                    "ItemType" to JsonPrimitive("Series"),
                    "DisabledMetadataFetchers" to JsonArray(listOf(JsonPrimitive("TheTVDB"))),
                ),
            ),
            changed.entryOf("Series"),
        )
    }

    @Test
    fun `a name is matched without regard to case, and a repeat switch does not list it twice`() {
        val once = configuration.withFetcher("Movie", FetcherKind.Metadata, "TheMovieDb", enabled = false)
        val twice = once.withFetcher("Movie", FetcherKind.Metadata, "themoviedb", enabled = false)

        assertEquals(JsonArray(listOf(JsonPrimitive("themoviedb"))), twice.entryOf("Movie").getValue("DisabledMetadataFetchers"))
    }

    @Test
    fun `fetchers show in the order the entry asks, then the rest as the server offers them`() {
        val offered = listOf("TheMovieDb", "The Open Movie Database", "TheTVDB")
        val ordered = configuration.withFetcherMoved("Movie", FetcherKind.Metadata, offered, "TheTVDB", -1)
        val names = ordered.fetchers("Movie", FetcherKind.Metadata, offered).map { it.name }

        assertEquals(listOf("TheMovieDb", "TheTVDB", "The Open Movie Database"), names)
        assertEquals(
            JsonArray(listOf("TheMovieDb", "TheTVDB", "The Open Movie Database").map(::JsonPrimitive)),
            ordered.entryOf("Movie").getValue("MetadataFetcherOrder"),
        )
    }

    @Test
    fun `a move changes that order list of that entry and nothing else`() {
        val offered = listOf("a", "b", "c")
        val changed = configuration.withFetcherMoved("MusicVideo", FetcherKind.Images, offered, "a", 1)

        assertEquals(JsonArray(listOf("b", "a", "c").map(::JsonPrimitive)), changed.entryOf("MusicVideo").getValue("ImageFetcherOrder"))
        assertEquals(
            configuration.entryOf("MusicVideo").getValue("DisabledImageFetchers"),
            changed.entryOf("MusicVideo").getValue("DisabledImageFetchers"),
        )
        assertEquals(configuration.entryOf("Movie"), changed.entryOf("Movie"))
        assertEquals(configuration.getValue("Future"), changed.getValue("Future"))
    }

    @Test
    fun `a name at the end cannot move past it and changes nothing, and the first move for a type with no entry writes one`() {
        val offered = listOf("a", "b")

        assertEquals(configuration, configuration.withFetcherMoved("Series", FetcherKind.Metadata, offered, "a", -1))
        val moved = configuration.withFetcherMoved("Series", FetcherKind.Metadata, offered, "b", -1)
        assertEquals(listOf("Movie", "MusicVideo", "Series"), moved.metadataTypes())
        assertEquals(JsonArray(listOf("b", "a").map(::JsonPrimitive)), moved.entryOf("Series").getValue("MetadataFetcherOrder"))
    }

    @Test
    fun `a place counts the offered names only, so a stale disabled name has none and does not widen the others`() {
        val offered = listOf("a", "b")
        val stale = configuration.withFetcher("Movie", FetcherKind.Metadata, "Old", enabled = false)

        assertEquals(1 to 2, stale.fetcherPlace("Movie", FetcherKind.Metadata, offered, "b"))
        assertEquals(null, stale.fetcherPlace("Movie", FetcherKind.Metadata, offered, "Old"))
        assertEquals(stale, stale.withFetcherMoved("Movie", FetcherKind.Metadata, offered, "b", 1))
    }

    private fun JsonObject.entryOf(type: String): JsonObject =
        getValue("MetadataOptions").let { it as JsonArray }.map { it.jsonObject }.first { it.getValue("ItemType") == JsonPrimitive(type) }
}
