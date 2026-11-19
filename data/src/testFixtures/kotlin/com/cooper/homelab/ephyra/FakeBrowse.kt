package com.cooper.homelab.ephyra

import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import java.util.concurrent.CopyOnWriteArrayList

/** One title in a library on the fake server, with what the browse reads filter and order by. */
data class FakeTitle(
    val id: String,
    val name: String,
    val libraryId: String,
    val type: String = "Movie",
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val officialRating: String? = null,
    val rating: Double? = null,
    val played: Boolean = false,
    val favourite: Boolean = false,
    val added: Int = 0,
    val positionTicks: Long = 0L,
    /** Orders continue watching, newest first. */
    val lastPlayed: Int = 0,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * The server's browse reads (`/Items` under a `parentId`, and `/Items/Filters`), answered from [titles] in the shapes the
 * OpenAPI document gives them. [FakeJellyfin] asks it first, so a request it does not serve falls through to the rest.
 */
class FakeBrowse {
    val titles = CopyOnWriteArrayList<FakeTitle>()

    /** The `/Items` requests this answered, to read the query a read sent. */
    val itemRequests = CopyOnWriteArrayList<RecordedRequest>()

    internal fun answer(request: RecordedRequest): MockResponse? {
        val url = request.url
        return when {
            url.encodedPath == "/Items/Filters" -> filters(request)
            // The card reads name their image types; the LIBRARY contract's own reads of these routes do not.
            url.queryParameter("enableImageTypes") == null -> null
            url.encodedPath == "/UserItems/Resume" -> resume(request)
            url.encodedPath == "/Shows/NextUp" -> nextUp(request)
            url.encodedPath == "/Items/Latest" -> latest(request)
            url.encodedPath == "/Items" && url.queryParameter("parentId") != null -> items(request)
            else -> null
        }
    }

    private fun inLibrary(request: RecordedRequest): List<FakeTitle> {
        val url = request.url
        val types = url.queryParameter("includeItemTypes")?.split(',').orEmpty()
        return titles.filter { it.libraryId == url.queryParameter("parentId") && (types.isEmpty() || it.type in types) }
    }

    private fun filters(request: RecordedRequest): MockResponse {
        val list = inLibrary(request)
        val genres = list.flatMap { it.genres }.distinct().joinToString(",") { "\"$it\"" }
        val years = list.mapNotNull { it.year }.distinct().joinToString(",")
        val ratings = list.mapNotNull { it.officialRating }.distinct().joinToString(",") { "\"$it\"" }
        return json("""{"Genres":[$genres],"Years":[$years],"OfficialRatings":[$ratings],"Tags":[]}""")
    }

    private fun items(request: RecordedRequest): MockResponse {
        itemRequests += request
        val url = request.url
        val genres = url.queryParameter("genres")?.split('|')
        val ratings = url.queryParameter("officialRatings")?.split('|')
        val years = url.queryParameter("years")?.split(',')?.map(String::toInt)
        val played = url.queryParameter("isPlayed")?.toBooleanStrictOrNull()
        val favourite = url.queryParameter("isFavorite")?.toBooleanStrictOrNull()
        val matching =
            inLibrary(request).filter { title ->
                (genres == null || title.genres.any { it in genres }) &&
                    (ratings == null || title.officialRating in ratings) &&
                    (years == null || title.year in years) &&
                    (played == null || title.played == played) &&
                    (favourite != true || title.favourite)
            }
        val ordered = matching.sortedWith(comparator(url.queryParameter("sortBy"), url.queryParameter("sortOrder") == "Descending"))
        val start = url.queryParameter("startIndex")?.toInt() ?: 0
        val limit = url.queryParameter("limit")?.toInt() ?: ordered.size
        val page = ordered.drop(start).take(limit)
        // The server leaves the count out when it is not asked for it.
        val total = if (url.queryParameter("enableTotalRecordCount") == "false") "" else ",\"TotalRecordCount\":${ordered.size}"
        return json("""{"Items":[${page.joinToString(",") { it.toJson() }}]$total,"StartIndex":$start}""")
    }

    private fun resume(request: RecordedRequest): MockResponse {
        itemRequests += request
        val inProgress = titles.filter { it.positionTicks > 0 && !it.played }.filter { inParent(request, it) }
        return pageOf(request, inProgress.sortedByDescending { it.lastPlayed })
    }

    /** The first unwatched episode of each series that has a watched one. */
    private fun nextUp(request: RecordedRequest): MockResponse {
        itemRequests += request
        val episodes = titles.filter { it.type == "Episode" && it.seriesId != null }.filter { inParent(request, it) }
        val next =
            episodes
                .groupBy { it.seriesId }
                .values
                .mapNotNull { series ->
                    if (series.none { it.played }) return@mapNotNull null
                    series.filter { !it.played }.minWithOrNull(compareBy({ it.season }, { it.episode }))
                }
        return pageOf(request, next.sortedBy { it.name })
    }

    private fun latest(request: RecordedRequest): MockResponse {
        itemRequests += request
        val url = request.url
        val played = url.queryParameter("isPlayed")?.toBooleanStrictOrNull()
        val limit = url.queryParameter("limit")?.toInt() ?: Int.MAX_VALUE
        val newest = inLibrary(request).filter { played == null || it.played == played }.sortedByDescending { it.added }.take(limit)
        return json("[${newest.joinToString(",") { it.toJson() }}]")
    }

    private fun inParent(
        request: RecordedRequest,
        title: FakeTitle,
    ) = request.url.queryParameter("parentId")?.let { it == title.libraryId } ?: true

    private fun pageOf(
        request: RecordedRequest,
        list: List<FakeTitle>,
    ): MockResponse {
        val url = request.url
        val start = url.queryParameter("startIndex")?.toInt() ?: 0
        val limit = url.queryParameter("limit")?.toInt() ?: list.size
        val total = if (url.queryParameter("enableTotalRecordCount") == "false") "" else ",\"TotalRecordCount\":${list.size}"
        return json("""{"Items":[${list.drop(start).take(limit).joinToString(",") { it.toJson() }}]$total,"StartIndex":$start}""")
    }

    private fun comparator(
        sortBy: String?,
        descending: Boolean,
    ): Comparator<FakeTitle> {
        val first = sortBy?.substringBefore(',')
        val key: Comparator<FakeTitle> =
            when (first) {
                "ProductionYear" -> compareBy(nullsLast()) { it.year }
                "CommunityRating" -> compareBy(nullsLast()) { it.rating }
                "DateCreated" -> compareBy { it.added }
                else -> compareBy { it.name.lowercase() }
            }
        return (if (descending) key.reversed() else key).thenBy { it.name.lowercase() }
    }

    private fun FakeTitle.toJson(): String =
        buildString {
            append("""{"Id":"$id","Name":"$name","Type":"$type"""")
            year?.let { append(""","ProductionYear":$it""") }
            rating?.let { append(""","CommunityRating":$it""") }
            officialRating?.let { append(""","OfficialRating":"$it"""") }
            append(""","RunTimeTicks":72000000000,"PrimaryImageAspectRatio":0.6667""")
            append(""","ImageTags":{"Primary":"p-$id"},"BackdropImageTags":["b-$id"]""")
            seriesId?.let { append(""","SeriesId":"$it","SeriesName":"${seriesName.orEmpty()}","SeriesPrimaryImageTag":"sp-$it"""") }
            season?.let { append(""","ParentIndexNumber":$it""") }
            episode?.let { append(""","IndexNumber":$it""") }
            append(""","UserData":{"Played":$played,"IsFavorite":$favourite,"PlaybackPositionTicks":$positionTicks}}""")
        }

    private fun json(body: String): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}
