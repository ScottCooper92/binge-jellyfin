package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

private const val HTTP_BAD_REQUEST = 400

private const val DEFAULT_OPTIONS =
    """
    {"Enabled":true,"EnableRealtimeMonitor":true,"EnableLUFSScan":false,"EnableChapterImageExtraction":false,
     "ExtractChapterImagesDuringLibraryScan":false,"EnableTrickplayImageExtraction":false,"ExtractTrickplayImagesDuringLibraryScan":false,
     "SaveLocalMetadata":false,"SaveTrickplayWithMedia":false,"SaveSubtitlesWithMedia":true,"SaveLyricsWithMedia":false,
     "EnableAutomaticSeriesGrouping":false,"EnableEmbeddedTitles":false,"EnableEmbeddedExtrasTitles":false,"EnableEmbeddedEpisodeInfos":false,
     "AutomaticRefreshIntervalDays":0,"PreferredMetadataLanguage":"en","MetadataCountryCode":"GB","SeasonZeroDisplayName":"Specials",
     "MetadataSavers":[],"DisabledLocalMetadataReaders":[],"LocalMetadataReaderOrder":["Nfo"],"DisabledSubtitleFetchers":[],
     "SubtitleFetcherOrder":["Open Subtitles"],"DisabledLyricFetchers":[],"LyricFetcherOrder":[],"DisabledMediaSegmentProviders":[],
     "MediaSegmentProviderOrder":[],"SkipSubtitlesIfEmbeddedSubtitlesPresent":false,"SkipSubtitlesIfAudioTrackMatches":false,
     "SubtitleDownloadLanguages":["eng"],"RequirePerfectSubtitleMatch":true,"AllowEmbeddedSubtitles":"AllowAll","TypeOptions":[]}
    """

/** The server's libraries, which can be added, renamed, given other folders and set up while the demo is open. */
internal class DemoLibraries : DemoState {
    private val libraries: MutableList<JsonObject> = seed()

    private fun seed() =
        mutableListOf(
            library("Movies", "movies", listOf("/media/movies")),
            library("TV shows", "tvshows", listOf("/media/tv")),
            library("Music", "music", listOf("/media/music")),
        )

    override fun reset() {
        libraries.clear()
        libraries.addAll(seed())
    }

    /** The libraries as a user's views: their id, name and kind. */
    fun views(): List<JsonObject> =
        libraries.map { library ->
            buildJsonObject {
                put("Id", library.string("ItemId").orEmpty())
                put("Name", library.string("Name").orEmpty())
                library.string("CollectionType")?.let { put("CollectionType", it) }
            }
        }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Library", "VirtualFolders") { _, _ -> ok(libraries.toList()) },
            route("POST", "Library", "VirtualFolders") { call, _ -> add(call) },
            route("DELETE", "Library", "VirtualFolders") { call, _ -> remove(call.query("name")) },
            route("POST", "Library", "VirtualFolders", "Name") { call, _ -> rename(call.query("name"), call.query("newName")) },
            route("POST", "Library", "VirtualFolders", "Paths") { call, _ -> addPath(call.jsonBody()) },
            route("DELETE", "Library", "VirtualFolders", "Paths") { call, _ -> removePath(call.query("name"), call.query("path")) },
            route("POST", "Library", "VirtualFolders", "LibraryOptions") { call, _ -> saveOptions(call.jsonBody()) },
            route("POST", "Items", "{}", "Refresh") { _, _ -> noContent },
        )

    private fun indexOf(name: String?) = libraries.indexOfFirst { it.string("Name") == name }

    private fun add(call: DemoCall): DemoAnswer {
        val name = call.query("name")?.trim().orEmpty()
        if (name.isEmpty() || indexOf(name) >= 0) return DemoAnswer(HTTP_BAD_REQUEST, """{"title":"A library with that name exists."}""")
        val paths =
            call
                .query("paths")
                ?.split(',')
                ?.filter { it.isNotBlank() }
                .orEmpty()
        libraries += library(name, call.query("collectionType").orEmpty(), paths)
        return noContent
    }

    private fun remove(name: String?): DemoAnswer = if (libraries.removeAll { it.string("Name") == name }) noContent else demoNotFound

    private fun rename(
        name: String?,
        newName: String?,
    ): DemoAnswer {
        val index = indexOf(name)
        if (index < 0 ||
            newName.isNullOrBlank() ||
            indexOf(newName) >= 0
        ) {
            return DemoAnswer(HTTP_BAD_REQUEST, """{"title":"That name cannot be used."}""")
        }
        libraries[index] = libraries[index].with("Name", JsonPrimitive(newName.trim()))
        return noContent
    }

    private fun addPath(body: JsonObject): DemoAnswer {
        val index = indexOf(body.string("Name"))
        val path = (body["PathInfo"] as? JsonObject)?.string("Path") ?: return demoNotFound
        if (index < 0) return demoNotFound
        val paths = libraries[index].locations() + path
        libraries[index] = libraries[index].with("Locations", JsonArray(paths.distinct().map(::JsonPrimitive)))
        return noContent
    }

    private fun removePath(
        name: String?,
        path: String?,
    ): DemoAnswer {
        val index = indexOf(name)
        if (index < 0) return demoNotFound
        libraries[index] =
            libraries[index].with("Locations", JsonArray(libraries[index].locations().filter { it != path }.map(::JsonPrimitive)))
        return noContent
    }

    private fun saveOptions(body: JsonObject): DemoAnswer {
        val index = libraries.indexOfFirst { it.string("ItemId") == body.string("Id") }
        val options = body["LibraryOptions"] ?: return demoNotFound
        if (index < 0) return demoNotFound
        libraries[index] = libraries[index].with("LibraryOptions", options)
        return noContent
    }

    private fun JsonObject.locations() = (get("Locations") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    private fun library(
        name: String,
        kind: String,
        paths: List<String>,
    ) = buildJsonObject {
        put("Name", name)
        put("ItemId", UUID.nameUUIDFromBytes(name.toByteArray()).toString().replace("-", ""))
        if (kind.isNotEmpty()) put("CollectionType", kind)
        put("Locations", JsonArray(paths.map(::JsonPrimitive)))
        put("LibraryOptions", jsonObject(DEFAULT_OPTIONS))
    }
}
