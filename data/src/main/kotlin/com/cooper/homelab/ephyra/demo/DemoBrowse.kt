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

/**
 * The demo's titles: a few movies and series with the episodes a continue watching and next up row needs. It answers the
 * browse reads (`/Items` under a `parentId`, `/Items/Filters`, resume, next up and latest). `/Items` without a `parentId`
 * is not its to answer: the LIBRARY index reads that, and must keep finding nothing, so the demo tells Binge of no titles.
 */
internal class DemoBrowse(
    private val libraries: DemoLibraries,
) : DemoState {
    private val titles: List<DemoTitle> = seedTitles()

    override fun reset() = Unit

    override fun routes(): List<DemoRoute> =
        listOf(
            route("GET", "Items", "Filters") { call, _ -> ok(filters(call)) },
            route("GET", "Items", "Latest") { call, _ -> ok(latest(call)) },
            route("GET", "Users", "{}", "Items", "Latest") { call, _ -> ok(latest(call)) },
            route("GET", "UserItems", "Resume") { call, _ -> ok(page(call, resume(call))) },
            route("GET", "Users", "{}", "Items", "Resume") { call, _ -> ok(page(call, resume(call))) },
            route("GET", "Shows", "NextUp") { call, _ -> ok(page(call, nextUp(call))) },
            route("GET", "Shows", "{}", "Seasons") { call, parts -> ok(page(call, children(parts.first(), "Season", null))) },
            route(
                "GET",
                "Shows",
                "{}",
                "Episodes",
            ) { call, parts -> ok(page(call, children(parts.first(), "Episode", call.query("seasonId")))) },
            route("GET", "Items", "{}") { _, parts -> title(parts.first()) },
            route("GET", "Users", "{}", "Items", "{}") { _, parts -> title(parts.last()) },
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

    private fun children(
        seriesId: String,
        type: String,
        seasonId: String?,
    ) = titles.filter { it.type == type && it.seriesId == seriesId && (seasonId == null || it.seasonId() == seasonId) }

    /** One title by id, with what only a title page reads. */
    private fun title(id: String): DemoAnswer {
        val found = titles.firstOrNull { it.id == id } ?: return demoNotFound
        val detail = json(DETAIL).let { it as JsonObject }
        return ok(JsonObject(found.toJson() + detail))
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
        val newest = inLibrary(call).filter { !hidePlayed || !it.played }.sortedByDescending { it.added }.take(limit)
        return JsonArray(newest.map { it.toJson() })
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
            if (type == "Season") {
                put("IndexNumber", season ?: 0)
                put("ChildCount", titles.count { it.seriesId == seriesId && it.season == season && it.type == "Episode" })
            } else {
                season?.let { put("ParentIndexNumber", it) }
                episode?.let { put("IndexNumber", it) }
            }
            if (type == "Episode") put("SeasonId", seasonId())
            putJsonObject("UserData") {
                put("Played", played)
                put("IsFavorite", favourite)
                put("PlaybackPositionTicks", positionTicks)
            }
        }
}
