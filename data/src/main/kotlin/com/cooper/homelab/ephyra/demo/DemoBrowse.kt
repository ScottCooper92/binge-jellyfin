package com.cooper.homelab.ephyra.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val POSTER_ASPECT = 0.6667
private const val SIMILAR_LIMIT = 12

/**
 * The demo's titles: a few movies and series with the episodes a continue watching and next up row needs. It answers the
 * browse reads (`/Items` under a `parentId`, `/Items/Filters`, resume, next up and latest). `/Items` without a `parentId`
 * is not its to answer: the LIBRARY index reads that, and must keep finding nothing, so the demo tells Binge of no titles.
 */
internal class DemoBrowse(
    private val libraries: DemoLibraries,
) : DemoState {
    private var titles: List<DemoTitle> = seedTitles()

    override fun reset() {
        titles = seedTitles()
    }

    override fun routes(): List<DemoRoute> =
        listOf(
            route("POST", "UserPlayedItems", "{}") { _, parts -> writeUserData(titles, parts.first(), played = true) },
            route("DELETE", "UserPlayedItems", "{}") { _, parts -> writeUserData(titles, parts.first(), played = false) },
            route("POST", "UserFavoriteItems", "{}") { _, parts -> writeUserData(titles, parts.first(), favourite = true) },
            route("DELETE", "UserFavoriteItems", "{}") { _, parts -> writeUserData(titles, parts.first(), favourite = false) },
            route("POST", "Users", "{}", "PlayedItems", "{}") { _, parts -> writeUserData(titles, parts.last(), played = true) },
            route("DELETE", "Users", "{}", "PlayedItems", "{}") { _, parts -> writeUserData(titles, parts.last(), played = false) },
            route("POST", "Users", "{}", "FavoriteItems", "{}") { _, parts -> writeUserData(titles, parts.last(), favourite = true) },
            route("DELETE", "Users", "{}", "FavoriteItems", "{}") { _, parts -> writeUserData(titles, parts.last(), favourite = false) },
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
            route("GET", "Persons") { call, _ -> ok(searchPeople(call.query("searchTerm").orEmpty())) },
            route("GET", "Items", "{}", "Similar") { call, parts ->
                ok(
                    page(
                        call,
                        titles.filter { it.type == "Movie" && it.id != parts.first() }.take(
                            call.query("limit")?.toIntOrNull() ?: SIMILAR_LIMIT,
                        ),
                    ),
                )
            },
            // The box sets a film is in. The route is 12.0 and later, as on a real server; the demo's own version reads the box sets instead.
            route("GET", "Items", "{}", "Collections") { call, parts ->
                ok(page(call, titles.filter { it.type == "BoxSet" && parts.first() in it.members }))
            },
            route("GET", "Items", "{}") { _, parts -> title(parts.first()) },
            route("GET", "Users", "{}", "Items", "{}") { _, parts -> title(parts.last()) },
        )

    /**
     * A `/Items` read the demo answers: a search by name, the titles of a person, or a library's titles. Null for any other,
     * with no `parentId` or the like, so the LIBRARY index's read still finds nothing.
     */
    fun items(call: DemoCall): JsonObject? {
        val outsideALibrary =
            call.query("searchTerm")?.let { search(call, it) }
                ?: call.query("personIds")?.let {
                    page(
                        call,
                        titles.creditsOf(
                            it,
                            call.query("includeItemTypes")?.split(',') ?: listOf("Movie", "Series"),
                        ),
                    )
                }
                // Before 12.0 a film's box sets are found by listing them, with no parent.
                ?: if (call.query("parentId") == null && call.query("includeItemTypes") == "BoxSet") page(call, titles.boxSets()) else null
        if (outsideALibrary != null) return outsideALibrary
        call.query("parentId") ?: return null
        val matching = inLibrary(call).filter { it.passes(call) }
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

    /** `searchTerm` over every title, as the server matches it: the name contains the term, ignoring case. */
    private fun search(
        call: DemoCall,
        term: String,
    ): JsonObject {
        val types = call.query("includeItemTypes")?.split(',').orEmpty()
        return page(call, titles.filter { it.name.contains(term, ignoreCase = true) && (types.isEmpty() || it.type in types) })
    }

    private fun children(
        seriesId: String,
        type: String,
        seasonId: String?,
    ) = titles
        .filter { it.type == type && it.seriesId == seriesId && (seasonId == null || it.seasonId() == seasonId) }
        .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: Int.MAX_VALUE }))

    /** One title by id, with what only a title page reads. */
    private fun title(id: String): DemoAnswer {
        PEOPLE[id]?.let { return ok(jsonObject(it)) }
        val found = titles.firstOrNull { it.id == id } ?: return demoNotFound
        return ok(JsonObject(found.toJson() + found.detail()))
    }

    private fun kindOf(call: DemoCall): String? =
        libraries.views().firstOrNull { it.string("Id") == call.query("parentId") }?.string("CollectionType")

    private fun inLibrary(call: DemoCall): List<DemoTitle> {
        val parent = call.query("parentId")
        // A box set's own children are its parts.
        val box = titles.firstOrNull { it.id == parent && it.type == "BoxSet" }
        val types = libraryTypes(kindOf(call), isView = libraries.views().any { it.string("Id") == parent })
        return if (box != null) titles.filter { it.id in box.members } else titles.filter { it.type in types }
    }

    private fun resume(call: DemoCall): List<DemoTitle> =
        titles.filter { it.positionTicks > 0 && !it.played }.sortedByDescending { it.added }.filter { inParent(call, it) }

    /** The first unwatched episode of each series that has a watched one. */
    private fun nextUp(call: DemoCall): List<DemoTitle> =
        titles
            // As the server does, specials are not next up.
            .filter { it.type == "Episode" && it.season != 0 }
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
            if (year > 0) put("ProductionYear", year)
            if (score > 0.0) put("CommunityRating", score)
            if (rating.isNotEmpty()) put("OfficialRating", rating)
            put("RunTimeTicks", runtimeTicks)
            put("PrimaryImageAspectRatio", POSTER_ASPECT)
            putJsonObject("ImageTags") { put("Primary", "p-$id") }
            if (hasBackdrop) putJsonArray("BackdropImageTags") { add(JsonPrimitive("b-$id")) }
            if (type == "BoxSet") put("ChildCount", members.size)
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
