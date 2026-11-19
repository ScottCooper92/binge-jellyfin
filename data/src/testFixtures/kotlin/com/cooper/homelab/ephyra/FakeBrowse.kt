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
            append(""","UserData":{"Played":$played,"IsFavorite":$favourite,"PlaybackPositionTicks":0}}""")
        }

    private fun json(body: String): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()
}
