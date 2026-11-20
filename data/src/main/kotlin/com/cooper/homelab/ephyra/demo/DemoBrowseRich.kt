package com.cooper.homelab.ephyra.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal const val RUNTIME_TICKS = 72_000_000_000L
private const val SECOND_TICKS = 10_000_000L
private const val MINUTE_TICKS = 60 * SECOND_TICKS
private const val EPISODE_TICKS = 47 * MINUTE_TICKS
private const val GENERATED_FILMS = 70
private const val ADJECTIVE_COUNT = 10
private const val FIRST_YEAR = 1990
private const val SERIES_YEAR = 2021
private const val YEAR_STEP = 7
private const val YEAR_SPAN = 35
private const val SCORE_FLOOR = 5.0
private const val SCORE_STEP = 37
private const val SCORE_SPAN = 41
private const val SCORE_DIVISOR = 10.0
private const val SECOND_GENRE_EVERY = 5
private const val SECOND_GENRE_OFFSET = 3
private const val PLAYED_EVERY = 7
private const val FAVOURITE_EVERY = 11
private const val SECOND_CAST_EVERY = 4
private const val WATCHED_EPISODES = 4
private const val PART_WATCHED_EPISODE = 5
private const val PART_WATCHED_MINUTES = 20
private const val SECOND_SEASON_EPISODES = 4
private const val SHORT_FILM_SECONDS = 30

internal const val SAM_RIVERS = "demo-person-1"
internal const val ALEX_MOOR = "demo-person-2"
internal const val ROBIN_HALE = "demo-person-3"
internal const val QUINN_ASH = "demo-person-4"

private val ADJECTIVES = listOf("Amber", "Quiet", "Hollow", "Silver", "Distant", "Paper", "Northern", "Winter", "Copper", "Velvet")
private val NOUNS = listOf("Coast", "Garden", "Letters", "Harbour", "Orchard", "Signal", "Meridian")
private val GENRES = listOf("Drama", "Comedy", "Thriller", "Adventure", "Family", "Animation", "Documentary", "Mystery")
private val RATINGS = listOf("G", "PG", "PG-13", "R", "15", "18")
private val EPISODE_NAMES =
    listOf(
        "The Lamp",
        "Low Tide",
        "What the Keeper Knew",
        "A Letter From the Sea",
        "Dead Reckoning",
        "The Long Way Round",
        "Salt and Iron",
        "Fog Signal",
        "The Relief Boat",
        "Last Light",
    )

/** Who is in the original sample titles, so a person's credits are real: the cast they list is in the titles that list them. */
internal fun originalCast(id: String): List<String> =
    when (id) {
        "demo-movie-1", "demo-movie-2", "demo-movie-3", "demo-series-1" -> listOf(SAM_RIVERS, ALEX_MOOR)
        "demo-movie-5", "demo-series-3" -> listOf(ROBIN_HALE)
        else -> emptyList()
    }

/**
 * What the client's screens need beyond the original sample: more than a page of films with genres, years and ratings that
 * filter, a film with two versions and one under a minute, box sets with a poster and no backdrop (one film is in two), and a
 * series with a special, an unnumbered episode and a season of ten. Ids are stable, so a test and a tap name the same title.
 */
internal fun richTitles(): List<DemoTitle> = films() + specialFilms() + boxSets() + lighthouseYears()

private fun films(): List<DemoTitle> =
    (0 until GENERATED_FILMS).map { i ->
        DemoTitle(
            id = "demo-film-${i + 1}",
            name = "${ADJECTIVES[i % ADJECTIVE_COUNT]} ${NOUNS[i / ADJECTIVE_COUNT]}",
            type = "Movie",
            year = FIRST_YEAR + (i * YEAR_STEP) % YEAR_SPAN,
            genres =
                listOfNotNull(
                    GENRES[i % GENRES.size],
                    GENRES[(i + SECOND_GENRE_OFFSET) % GENRES.size].takeIf {
                        i % SECOND_GENRE_EVERY ==
                            0
                    },
                ),
            rating = RATINGS[i % RATINGS.size],
            score = SCORE_FLOOR + (i * SCORE_STEP % SCORE_SPAN) / SCORE_DIVISOR,
            added = -(i + 1),
            played = i % PLAYED_EVERY == 0,
            favourite = i % FAVOURITE_EVERY == 0,
            cast = listOfNotNull(SAM_RIVERS, ROBIN_HALE.takeIf { i % SECOND_CAST_EVERY == 0 }),
        )
    }

private fun specialFilms(): List<DemoTitle> =
    listOf(
        DemoTitle(
            id = "demo-movie-7",
            name = "Two Cuts",
            type = "Movie",
            year = 2020,
            genres = listOf("Drama"),
            rating = "PG-13",
            score = 7.3,
            added = 3,
            versions = 2,
            cast = listOf(SAM_RIVERS, ROBIN_HALE),
        ),
        DemoTitle(
            id = "demo-movie-9",
            name = "Blink",
            type = "Movie",
            year = 2016,
            genres = listOf("Animation"),
            rating = "G",
            score = 6.5,
            added = 2,
            runtimeTicks = SHORT_FILM_SECONDS * SECOND_TICKS,
        ),
    )

private fun boxSets(): List<DemoTitle> =
    listOf(
        boxSet("demo-box-1", "The Quiet Collection", 2, listOf("demo-movie-1", "demo-movie-2", "demo-movie-3", "demo-movie-4")),
        // Salt Road is in both box sets, as a film can be.
        boxSet("demo-box-2", "Late Night Trilogy", 1, listOf("demo-movie-4", "demo-movie-5", "demo-movie-6")),
    )

private fun boxSet(
    id: String,
    name: String,
    added: Int,
    members: List<String>,
) = DemoTitle(id, name, "BoxSet", 0, emptyList(), "", 0.0, added, members = members, hasBackdrop = false)

/** A series of two seasons, ten episodes in the first and four in the second, and specials with one episode that has no number. */
private fun lighthouseYears(): List<DemoTitle> {
    val id = "demo-series-4"
    val name = "Lighthouse Years"

    fun season(number: Int) =
        DemoTitle(
            "$id-s$number",
            if (number ==
                0
            ) {
                "Specials"
            } else {
                "Season $number"
            },
            "Season",
            SERIES_YEAR,
            emptyList(),
            "",
            0.0,
            0,
            seriesId = id,
            seriesName = name,
            season = number,
        )

    fun episode(
        season: Int,
        number: Int?,
        title: String,
        played: Boolean = false,
        position: Long = 0L,
    ) = DemoTitle(
        id = "demo-ep-4-s$season-${number ?: "x"}",
        name = title,
        type = "Episode",
        year = SERIES_YEAR,
        genres = emptyList(),
        rating = "TV-14",
        score = 7.9,
        added = 0,
        played = played,
        positionTicks = position,
        seriesId = id,
        seriesName = name,
        season = season,
        episode = number,
        runtimeTicks = EPISODE_TICKS,
    )
    val firstSeason =
        EPISODE_NAMES.mapIndexed { index, title ->
            val number = index + 1
            episode(
                1,
                number,
                title,
                played = number <= WATCHED_EPISODES,
                position =
                    if (number ==
                        PART_WATCHED_EPISODE
                    ) {
                        PART_WATCHED_MINUTES * MINUTE_TICKS
                    } else {
                        0L
                    },
            )
        }
    val secondSeason = (1..SECOND_SEASON_EPISODES).map { episode(2, it, "Winter $it") }
    val specials = listOf(episode(0, 1, "Behind the Lamp"), episode(0, null, "Outtakes"))
    val series =
        DemoTitle(
            id,
            name,
            "Series",
            SERIES_YEAR,
            listOf("Drama", "Mystery"),
            "TV-14",
            8.6,
            3,
            cast = listOf(SAM_RIVERS, ROBIN_HALE, ALEX_MOOR),
        )
    return listOf(series, season(0), season(1), season(2)) + specials + firstSeason + secondSeason
}

/** One person the demo knows: whether they have a picture, and what is said of them. */
internal class DemoPerson(
    val id: String,
    val name: String,
    val overview: String?,
    val born: String?,
    val place: String?,
    val photo: Boolean,
    /** How a title page credits them: an actor with a role, or the director. */
    val type: String = "Actor",
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("Id", id)
            put("Name", name)
            put("Type", "Person")
            overview?.let { put("Overview", it) }
            born?.let { put("PremiereDate", it) }
            place?.let { put("ProductionLocations", JsonArray(listOf(JsonPrimitive(it)))) }
            if (photo) putJsonObject("ImageTags") { put("Primary", "p-$id") }
        }
}

/** Sam Rivers is in more titles than a page holds, Robin Hale has no picture, and Quinn Ash has no titles on the server. */
internal val PERSONS: List<DemoPerson> =
    listOf(
        DemoPerson(SAM_RIVERS, "Sam Rivers", "An actor.", "1980-03-02T00:00:00.0000000Z", "Leeds, UK", true),
        DemoPerson(ALEX_MOOR, "Alex Moor", "A director.", null, null, true, type = "Director"),
        DemoPerson(ROBIN_HALE, "Robin Hale", "An actor with no picture on file.", "1991-07-14T00:00:00.0000000Z", "Cork, IE", false),
        DemoPerson(QUINN_ASH, "Quinn Ash", "A writer with nothing on this server yet.", null, "Dundee, UK", true),
    )

/** What only a title page reads: the shared sample, with this title's genres, people, files and runtime in place of it. */
internal fun DemoTitle.detail(): JsonObject {
    val base = json(DETAIL) as JsonObject
    val playable = type == "Movie" || type == "Episode"
    return JsonObject(
        buildMap {
            putAll(base)
            if (genres.isNotEmpty()) put("Genres", JsonArray(genres.map(::JsonPrimitive)))
            // A title lists only the people it is credited to, so a person's page and a title's cast agree.
            if (cast.isNotEmpty()) put("People", people()) else remove("People")
            if (playable) put("MediaSources", JsonArray((1..versions).map { source(it) })) else remove("MediaSources")
            if (type == "BoxSet" || type == "Season") {
                remove("People")
                remove("Chapters")
                remove("ProviderIds")
            }
            if (type == "Episode") put("SeasonName", JsonPrimitive(if (season == 0) "Specials" else "Season $season"))
        },
    )
}

private fun DemoTitle.people(): JsonArray =
    JsonArray(
        cast.mapNotNull { id -> PERSONS.firstOrNull { it.id == id } }.map { person ->
            castMember(person.type, person, "Role".takeIf { person.type == "Actor" })
        },
    )

private fun castMember(
    type: String,
    person: DemoPerson,
    role: String?,
) = buildJsonObject {
    put("Id", person.id)
    put("Name", person.name)
    put("Type", type)
    role?.let { put("Role", it) }
    if (person.photo) put("PrimaryImageTag", "p-${person.id}")
}

private fun DemoTitle.source(number: Int): JsonObject =
    if (number == 1) {
        jsonObject(
            """{"Id":"demo-source","Name":"1080p","Container":"mkv","Size":4000000000,"Bitrate":8000000,
                "RunTimeTicks":$runtimeTicks,"DefaultAudioStreamIndex":1,"MediaStreams":[
                {"Index":0,"Type":"Video","Codec":"h264","Width":1920,"Height":1080,"VideoRange":"SDR","BitDepth":8},
                {"Index":1,"Type":"Audio","Codec":"aac","Language":"eng","Channels":2,"IsDefault":true,"DisplayTitle":"English - AAC - Stereo"},
                {"Index":2,"Type":"Subtitle","Codec":"subrip","Language":"eng","DisplayTitle":"English - SubRip"}]}""",
        )
    } else {
        jsonObject(
            """{"Id":"demo-source-$number","Name":"2160p HDR","Container":"mkv","Size":14800000000,"Bitrate":32000000,
                "RunTimeTicks":$runtimeTicks,"DefaultAudioStreamIndex":1,"MediaStreams":[
                {"Index":0,"Type":"Video","Codec":"hevc","Width":3840,"Height":2160,"VideoRange":"HDR","VideoRangeType":"HDR10","BitDepth":10},
                {"Index":1,"Type":"Audio","Codec":"eac3","Language":"eng","Channels":6,"IsDefault":true,"DisplayTitle":"English - EAC3 - 5.1"},
                {"Index":2,"Type":"Audio","Codec":"aac","Language":"spa","Channels":2,"DisplayTitle":"Spanish - AAC - Stereo"},
                {"Index":3,"Type":"Subtitle","Codec":"subrip","Language":"eng","DisplayTitle":"English - SubRip"}]}""",
        )
    }

/** The titles [personId] is in, by name, of the [types] asked for. */
internal fun List<DemoTitle>.creditsOf(
    personId: String,
    types: List<String>,
): List<DemoTitle> = filter { personId in it.cast && it.type in types }.sortedBy { it.name.lowercase() }

/** Every box set, in name order. */
internal fun List<DemoTitle>.boxSets(): List<DemoTitle> = filter { it.type == "BoxSet" }.sortedBy { it.name.lowercase() }

/** The item types a library of [kind] holds: a view with no kind (when [isView]) holds films and series together. */
internal fun libraryTypes(
    kind: String?,
    isView: Boolean,
): List<String> =
    when (kind) {
        "movies" -> listOf("Movie")
        "tvshows" -> listOf("Series")
        "boxsets" -> listOf("BoxSet")
        null -> if (isView) listOf("Movie", "Series") else emptyList()
        else -> emptyList()
    }
