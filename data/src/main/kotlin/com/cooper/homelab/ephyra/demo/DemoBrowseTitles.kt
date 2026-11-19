package com.cooper.homelab.ephyra.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One title in the demo's Movies or TV shows library. An episode has a [seriesId]. */
internal class DemoTitle(
    val id: String,
    val name: String,
    val type: String,
    val year: Int,
    val genres: List<String>,
    val rating: String,
    val score: Double,
    val added: Int,
    var played: Boolean = false,
    var favourite: Boolean = false,
    var positionTicks: Long = 0L,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/** The id of the season a title is in, or its own when it is a season. */
internal fun DemoTitle.seasonId() = if (type == "Season") id else "$seriesId-s$season"

/** Sample titles as the server would send them, so the numbers are data and not code. */
private const val TITLES =
    """
    [
     {"Id":"demo-movie-1","Name":"The Long Quiet","Type":"Movie","ProductionYear":2021,"Genres":["Drama"],"OfficialRating":"PG-13","CommunityRating":7.8,"Added":12,"Position":36000000000},
     {"Id":"demo-movie-2","Name":"Night Market","Type":"Movie","ProductionYear":2019,"Genres":["Thriller","Drama"],"OfficialRating":"R","CommunityRating":7.1,"Added":11,"Played":true},
     {"Id":"demo-movie-3","Name":"Paper Boats","Type":"Movie","ProductionYear":2023,"Genres":["Family","Animation"],"OfficialRating":"PG","CommunityRating":8.2,"Added":10,"Favourite":true},
     {"Id":"demo-movie-4","Name":"Salt Road","Type":"Movie","ProductionYear":2017,"Genres":["Adventure"],"OfficialRating":"PG-13","CommunityRating":6.9,"Added":9},
     {"Id":"demo-movie-5","Name":"The Understudy","Type":"Movie","ProductionYear":2022,"Genres":["Comedy"],"OfficialRating":"R","CommunityRating":7.4,"Added":8},
     {"Id":"demo-movie-6","Name":"Low Tide","Type":"Movie","ProductionYear":2015,"Genres":["Drama"],"OfficialRating":"PG","CommunityRating":7.0,"Added":7,"Played":true},
     {"Id":"demo-series-1","Name":"Harbour Lights","Type":"Series","ProductionYear":2020,"Genres":["Drama"],"OfficialRating":"TV-14","CommunityRating":8.4,"Added":6},
     {"Id":"demo-series-2","Name":"Field Notes","Type":"Series","ProductionYear":2018,"Genres":["Documentary"],"OfficialRating":"TV-PG","CommunityRating":8.0,"Added":5,"Favourite":true},
     {"Id":"demo-series-3","Name":"Second Shift","Type":"Series","ProductionYear":2022,"Genres":["Comedy"],"OfficialRating":"TV-14","CommunityRating":7.6,"Added":4},
     {"Id":"demo-series-1-s1","Name":"Season 1","Type":"Season","ProductionYear":2020,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Added":0},
     {"Id":"demo-series-3-s1","Name":"Season 1","Type":"Season","ProductionYear":2022,"SeriesId":"demo-series-3","SeriesName":"Second Shift","Season":1,"Added":0},
     {"Id":"demo-ep-1-1","Name":"Episode 1","Type":"Episode","ProductionYear":2020,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":1,"Played":true,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Episode":1},
     {"Id":"demo-ep-1-2","Name":"Episode 2","Type":"Episode","ProductionYear":2020,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":2,"Played":true,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Episode":2},
     {"Id":"demo-ep-1-3","Name":"Episode 3","Type":"Episode","ProductionYear":2020,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":3,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Episode":3},
     {"Id":"demo-ep-3-1","Name":"Episode 1","Type":"Episode","ProductionYear":2022,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":1,"Position":36000000000,"SeriesId":"demo-series-3","SeriesName":"Second Shift","Season":1,"Episode":1},
     {"Id":"demo-ep-3-2","Name":"Episode 2","Type":"Episode","ProductionYear":2022,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":2,"SeriesId":"demo-series-3","SeriesName":"Second Shift","Season":1,"Episode":2}
    ]
    """

/** What a title page reads and a card does not. The same sample for every title: the demo is about the screens. */
internal const val DETAIL =
    """
    {"Overview":"A quiet story told over one long night.","Taglines":["Some nights last."],"Genres":["Drama"],
     "Studios":[{"Name":"Demo Pictures"}],"People":[{"Id":"demo-person-1","Name":"Sam Rivers","Role":"Lead","Type":"Actor"},
     {"Id":"demo-person-2","Name":"Alex Moor","Type":"Director"}],
     "ProviderIds":{"Tmdb":"1"},
     "Chapters":[{"StartPositionTicks":0,"Name":"Opening"},{"StartPositionTicks":18000000000,"Name":"Night"}],
     "MediaSources":[{"Id":"demo-source","Name":"1080p","Container":"mkv","Size":4000000000,"Bitrate":8000000,
      "RunTimeTicks":72000000000,"DefaultAudioStreamIndex":1,"MediaStreams":[
      {"Index":0,"Type":"Video","Codec":"h264","Width":1920,"Height":1080,"VideoRange":"SDR","BitDepth":8},
      {"Index":1,"Type":"Audio","Codec":"aac","Language":"eng","Channels":2,"IsDefault":true,"DisplayTitle":"English - AAC - Stereo"},
      {"Index":2,"Type":"Subtitle","Codec":"subrip","Language":"eng","DisplayTitle":"English - SubRip"}]}]}
    """

internal fun seedTitles(): List<DemoTitle> =
    (json(TITLES) as JsonArray).map { element ->
        val title = element as JsonObject

        fun text(key: String) = (title[key] as? JsonPrimitive)?.content

        fun number(key: String) = text(key)?.toIntOrNull()
        DemoTitle(
            id = text("Id").orEmpty(),
            name = text("Name").orEmpty(),
            type = text("Type").orEmpty(),
            year = number("ProductionYear") ?: 0,
            genres = (title["Genres"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content },
            rating = text("OfficialRating").orEmpty(),
            score = text("CommunityRating")?.toDoubleOrNull() ?: 0.0,
            added = number("Added") ?: 0,
            played = text("Played") == "true",
            favourite = text("Favourite") == "true",
            positionTicks = text("Position")?.toLongOrNull() ?: 0L,
            seriesId = text("SeriesId"),
            seriesName = text("SeriesName"),
            season = number("Season"),
            episode = number("Episode"),
        )
    }

/** A played or favourite write: kept in the title, answered as the server does, with the item's user data. */
internal fun writeUserData(
    titles: List<DemoTitle>,
    id: String,
    played: Boolean? = null,
    favourite: Boolean? = null,
): DemoAnswer {
    val found = titles.firstOrNull { it.id == id } ?: return demoNotFound
    played?.let {
        found.played = it
        found.positionTicks = 0L
    }
    favourite?.let { found.favourite = it }
    return ok(
        buildJsonObject {
            put("Played", found.played)
            put("IsFavorite", found.favourite)
            put("PlaybackPositionTicks", found.positionTicks)
            put("ItemId", found.id)
        },
    )
}

/** The two people in every demo title's cast, as the item route sends a person. */
internal val PEOPLE: Map<String, String> =
    mapOf(
        "demo-person-1" to
            """{"Id":"demo-person-1","Name":"Sam Rivers","Type":"Person","Overview":"An actor.","PremiereDate":"1980-03-02T00:00:00.0000000Z","ProductionLocations":["Leeds, UK"]}""",
        "demo-person-2" to """{"Id":"demo-person-2","Name":"Alex Moor","Type":"Person","Overview":"A director."}""",
    )

/** Whether this title passes the filters the call names: genres, ratings, years, played and favourite. */
internal fun DemoTitle.passes(call: DemoCall): Boolean {
    val title = this
    val genres = call.query("genres")?.split('|')
    val ratings = call.query("officialRatings")?.split('|')
    val years = call.query("years")?.split(',')?.mapNotNull(String::toIntOrNull)
    val played = call.query("isPlayed")?.toBooleanStrictOrNull()
    return (genres == null || title.genres.any { it in genres }) &&
        (ratings == null || title.rating in ratings) &&
        (years == null || title.year in years) &&
        (played == null || title.played == played) &&
        (call.query("isFavorite") != "true" || title.favourite)
}
