package com.cooper.binge.companion.jellyfin.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What the server answers the same way every time: the lists its pickers offer, and the file system its folder browser walks. */
internal class DemoLookups(
    private val libraries: DemoLibraries,
) : DemoState {
    override fun reset() = Unit

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Localization", "Options") { _, _ -> ok(json(UI_LANGUAGES)) },
            route("GET", "Localization", "Cultures") { _, _ -> ok(json(CULTURES)) },
            route("GET", "Localization", "Countries") { _, _ -> ok(json(COUNTRIES)) },
            route("GET", "Localization", "ParentalRatings") { _, _ -> ok(json(RATINGS)) },
            route("GET", "Libraries", "AvailableOptions") { _, _ -> ok(json(AVAILABLE_OPTIONS)) },
            route("GET", "Environment", "Drives") { _, _ -> ok(json(DRIVES)) },
            route("GET", "Environment", "DirectoryContents") { call, _ -> ok(directory(call.query("path").orEmpty())) },
            route("POST", "Environment", "ValidatePath") { _, _ -> noContent },
            route("GET", "UserViews") { _, _ -> ok(views()) },
            route("GET", "Users", "{}", "Views") { _, _ -> ok(views()) },
            route("GET", "UserViews", "GroupingOptions") { _, _ -> ok(libraries.views()) },
            route("GET", "Users", "{}", "GroupingOptions") { _, _ -> ok(libraries.views()) },
        )

    private fun views() =
        buildJsonObject {
            put("Items", JsonArray(libraries.views()))
            put("TotalRecordCount", libraries.views().size)
        }

    private fun directory(path: String) =
        JsonArray(
            FOLDERS.filter { it.substringBeforeLast('/').ifEmpty { "/" } == path.trimEnd('/').ifEmpty { "/" } }.map { folder ->
                buildJsonObject {
                    put("Name", folder.substringAfterLast('/'))
                    put("Path", folder)
                    put("Type", "Directory")
                }
            },
        )

    private companion object {
        val FOLDERS = listOf("/media", "/media/movies", "/media/tv", "/media/music", "/media/anime", "/media/home-videos", "/data")

        const val DRIVES = """[{"Name":"/media","Path":"/media","Type":"Directory"},{"Name":"/data","Path":"/data","Type":"Directory"}]"""

        const val UI_LANGUAGES =
            """[{"Name":"English (United Kingdom)","Value":"en-GB"},{"Name":"English (United States)","Value":"en-US"},
               {"Name":"Español","Value":"es"},{"Name":"Français","Value":"fr"},{"Name":"Deutsch","Value":"de"},
               {"Name":"日本語","Value":"ja"}]"""

        const val CULTURES =
            """[{"Name":"English","DisplayName":"English","TwoLetterISOLanguageName":"en","ThreeLetterISOLanguageName":"eng","ThreeLetterISOLanguageNames":["eng"]},
               {"Name":"Spanish","DisplayName":"Spanish","TwoLetterISOLanguageName":"es","ThreeLetterISOLanguageName":"spa","ThreeLetterISOLanguageNames":["spa"]},
               {"Name":"French","DisplayName":"French","TwoLetterISOLanguageName":"fr","ThreeLetterISOLanguageName":"fra","ThreeLetterISOLanguageNames":["fra","fre"]},
               {"Name":"German","DisplayName":"German","TwoLetterISOLanguageName":"de","ThreeLetterISOLanguageName":"deu","ThreeLetterISOLanguageNames":["deu","ger"]},
               {"Name":"Japanese","DisplayName":"Japanese","TwoLetterISOLanguageName":"ja","ThreeLetterISOLanguageName":"jpn","ThreeLetterISOLanguageNames":["jpn"]}]"""

        const val COUNTRIES =
            """[{"Name":"GB","DisplayName":"United Kingdom","TwoLetterISORegionName":"GB","ThreeLetterISORegionName":"GBR"},
               {"Name":"US","DisplayName":"United States","TwoLetterISORegionName":"US","ThreeLetterISORegionName":"USA"},
               {"Name":"ES","DisplayName":"Spain","TwoLetterISORegionName":"ES","ThreeLetterISORegionName":"ESP"},
               {"Name":"FR","DisplayName":"France","TwoLetterISORegionName":"FR","ThreeLetterISORegionName":"FRA"},
               {"Name":"DE","DisplayName":"Germany","TwoLetterISORegionName":"DE","ThreeLetterISORegionName":"DEU"}]"""

        const val RATINGS =
            """[{"Name":"U","Value":1},{"Name":"PG","Value":5},{"Name":"12","Value":12},{"Name":"15","Value":15},{"Name":"18","Value":18}]"""

        const val AVAILABLE_OPTIONS =
            """{"MetadataReaders":[{"Name":"Nfo"}],"MetadataSavers":[{"Name":"Nfo","DefaultEnabled":false}],
                "SubtitleFetchers":[{"Name":"Open Subtitles"}],"LyricFetchers":[],"MediaSegmentProviders":[],
                "TypeOptions":[
                  {"Type":"Movie","MetadataFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true},{"Name":"The Open Movie Database","DefaultEnabled":true}],
                   "ImageFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true},{"Name":"Screen Grabber","DefaultEnabled":false}]},
                  {"Type":"Series","MetadataFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true},{"Name":"TheTVDB","DefaultEnabled":true}],
                   "ImageFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true},{"Name":"TheTVDB","DefaultEnabled":true}]},
                  {"Type":"Season","MetadataFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true}],"ImageFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true}]},
                  {"Type":"Episode","MetadataFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true}],"ImageFetchers":[{"Name":"TheMovieDb","DefaultEnabled":true}]}]}"""
    }
}
