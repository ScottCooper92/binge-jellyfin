package com.cooper.homelab.ephyra.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val RUNTIME_TICKS = 72_000_000_000L
private const val POSTER_ASPECT = 0.6667

/** One title in the demo's Movies or TV shows library. An episode has a [seriesId]. */
private class DemoTitle(
    val id: String,
    val name: String,
    val type: String,
    val year: Int,
    val genres: List<String>,
    val rating: String,
    val score: Double,
    val added: Int,
    val played: Boolean = false,
    val favourite: Boolean = false,
    val positionTicks: Long = 0L,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * The demo's titles: a few movies and series with the episodes a continue watching and next up row needs. It answers the
 * browse reads (`/Items` under a `parentId`, `/Items/Filters`, resume, next up and latest). `/Items` without a `parentId`
 * is not its to answer: the LIBRARY index reads that, and must keep finding nothing, so the demo tells Binge of no titles.
 */
internal class DemoBrowse(
    private val libraries: DemoLibraries,
) : DemoState {
    private val titles: List<DemoTitle> = seed()

    override fun reset() = Unit

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Items", "Filters") { call, _ -> ok(filters(call)) },
            route("GET", "Items", "Latest") { call, _ -> ok(latest(call)) },
            route("GET", "Users", "{}", "Items", "Latest") { call, _ -> ok(latest(call)) },
            route("GET", "UserItems", "Resume") { call, _ -> ok(page(call, resume(call))) },
            route("GET", "Users", "{}", "Items", "Resume") { call, _ -> ok(page(call, resume(call))) },
            route("GET", "Shows", "NextUp") { call, _ -> ok(page(call, nextUp(call))) },
        )

    /** A `/Items` read under a library, or null when there is no `parentId` and so it is not a browse read. */
    fun items(call: DemoCall): JsonObject? {
        call.query("parentId") ?: return null
        val genres = call.query("genres")?.split('|')
        val ratings = call.query("officialRatings")?.split('|')
        val years = call.query("years")?.split(',')?.mapNotNull(String::toIntOrNull)
        val played = call.query("isPlayed")?.toBooleanStrictOrNull()
        val matching =
            inLibrary(call).filter { title ->
                (genres == null || title.genres.any { it in genres }) &&
                    (ratings == null || title.rating in ratings) &&
                    (years == null || title.year in years) &&
                    (played == null || title.played == played) &&
                    (call.query("isFavorite") != "true" || title.favourite)
            }
        val key: Comparator<DemoTitle> =
            when (call.query("sortBy")?.substringBefore(',')) {
                "ProductionYear" -> compareBy { it.year }
                "CommunityRating" -> compareBy { it.score }
                "DateCreated" -> compareBy { it.added }
                else -> compareBy { it.name.lowercase() }
            }
        val ordered = matching.sortedWith(if (call.query("sortOrder") == "Descending") key.reversed() else key)
        return page(call, ordered)
    }

    private fun kindOf(call: DemoCall): String? =
        libraries.views().firstOrNull { it.string("Id") == call.query("parentId") }?.string("CollectionType")

    private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.content

    private fun inLibrary(call: DemoCall): List<DemoTitle> {
        val types =
            when (kindOf(call)) {
                "movies" -> listOf("Movie")
                "tvshows" -> listOf("Series")
                else -> return emptyList()
            }
        return titles.filter { it.type in types }
    }

    private fun resume(call: DemoCall): List<DemoTitle> =
        titles.filter { it.positionTicks > 0 && !it.played }.sortedByDescending { it.added }.filter { inParent(call, it) }

    /** The first unwatched episode of each series that has a watched one. */
    private fun nextUp(call: DemoCall): List<DemoTitle> =
        titles
            .filter { it.type == "Episode" }
            .groupBy { it.seriesId }
            .values
            .mapNotNull { episodes ->
                if (episodes.none { it.played }) return@mapNotNull null
                episodes.filter { !it.played && it.positionTicks == 0L }.minWithOrNull(compareBy({ it.season }, { it.episode }))
            }.filter { inParent(call, it) }

    private fun inParent(
        call: DemoCall,
        title: DemoTitle,
    ): Boolean {
        val parent = call.query("parentId") ?: return true
        val kind = if (title.type == "Movie") "movies" else "tvshows"
        return libraries.views().any { it.string("Id") == parent && it.string("CollectionType") == kind }
    }

    private fun latest(call: DemoCall): JsonArray {
        val limit = call.query("limit")?.toIntOrNull() ?: Int.MAX_VALUE
        val hidePlayed = call.query("isPlayed") == "false"
        val newest =
            if (kindOf(call) == "tvshows") {
                // As the server groups them: a series is as new as its newest unwatched episode.
                val series = titles.filter { it.type == "Series" }.associateBy { it.id }
                titles
                    .filter { it.type == "Episode" && (!hidePlayed || !it.played) }
                    .groupBy { it.seriesId }
                    .mapNotNull { (id, episodes) -> series[id]?.let { it to episodes.maxOf { episode -> episode.added } } }
                    .sortedByDescending { it.second }
                    .map { it.first }
            } else {
                inLibrary(call).filter { !hidePlayed || !it.played }.sortedByDescending { it.added }
            }
        return JsonArray(newest.take(limit).map { it.toJson() })
    }

    private fun filters(call: DemoCall): JsonObject {
        val list = inLibrary(call)
        return buildJsonObject {
            putJsonArray("Genres") { list.flatMap { it.genres }.distinct().forEach { add(JsonPrimitive(it)) } }
            putJsonArray("Years") { list.map { it.year }.distinct().forEach { add(JsonPrimitive(it)) } }
            putJsonArray("OfficialRatings") { list.map { it.rating }.distinct().forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun page(
        call: DemoCall,
        list: List<DemoTitle>,
    ): JsonObject {
        val start = call.query("startIndex")?.toIntOrNull() ?: 0
        val limit = call.query("limit")?.toIntOrNull() ?: list.size
        return buildJsonObject {
            put("Items", JsonArray(list.drop(start).take(limit).map { it.toJson() }))
            put("StartIndex", start)
            // The server leaves the count out unless it is asked for it.
            if (call.query("enableTotalRecordCount") != "false") put("TotalRecordCount", list.size)
        }
    }

    private fun DemoTitle.toJson(): JsonObject =
        buildJsonObject {
            put("Id", id)
            put("Name", name)
            put("Type", type)
            put("ProductionYear", year)
            put("CommunityRating", score)
            put("OfficialRating", rating)
            put("RunTimeTicks", RUNTIME_TICKS)
            put("PrimaryImageAspectRatio", POSTER_ASPECT)
            putJsonObject("ImageTags") { put("Primary", "p-$id") }
            putJsonArray("BackdropImageTags") { add(JsonPrimitive("b-$id")) }
            seriesId?.let {
                put("SeriesId", it)
                put("SeriesName", seriesName.orEmpty())
                put("SeriesPrimaryImageTag", "p-$it")
            }
            season?.let { put("ParentIndexNumber", it) }
            episode?.let { put("IndexNumber", it) }
            putJsonObject("UserData") {
                put("Played", played)
                put("IsFavorite", favourite)
                put("PlaybackPositionTicks", positionTicks)
            }
        }

    private companion object {
        /** Sample titles as the server would send them, so the numbers are data and not code. */
        const val TITLES =
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
             {"Id":"demo-ep-1-1","Name":"Episode 1","Type":"Episode","ProductionYear":2020,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":1,"Played":true,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Episode":1},
             {"Id":"demo-ep-1-2","Name":"Episode 2","Type":"Episode","ProductionYear":2020,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":2,"Played":true,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Episode":2},
             {"Id":"demo-ep-1-3","Name":"Episode 3","Type":"Episode","ProductionYear":2020,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":3,"SeriesId":"demo-series-1","SeriesName":"Harbour Lights","Season":1,"Episode":3},
             {"Id":"demo-ep-3-1","Name":"Episode 1","Type":"Episode","ProductionYear":2022,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":1,"Position":36000000000,"SeriesId":"demo-series-3","SeriesName":"Second Shift","Season":1,"Episode":1},
             {"Id":"demo-ep-3-2","Name":"Episode 2","Type":"Episode","ProductionYear":2022,"OfficialRating":"TV-14","CommunityRating":7.5,"Added":2,"SeriesId":"demo-series-3","SeriesName":"Second Shift","Season":1,"Episode":2}
            ]
            """

        fun seed(): List<DemoTitle> =
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
    }
}
