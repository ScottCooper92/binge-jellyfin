package com.cooper.homelab.ephyra.demo

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A film's or series' critics score, dates, trailer, web pages and tags: the reads a title page makes beyond its people and files. */
internal fun DemoTitle.titleExtras(): JsonObject =
    buildJsonObject {
        critics?.let { put("CriticRating", it) }
        if (year > 0) put("PremiereDate", "$year-03-01T00:00:00.0000000Z")
        if (type == "Series") {
            put("Status", if (ended) "Ended" else "Continuing")
            if (ended) put("EndDate", "${year + 1}-11-01T00:00:00.0000000Z")
        }
        if (hasExtras) {
            put("RemoteTrailers", JsonArray(listOf(urlEntry("Trailer", "https://www.youtube.com/watch?v=demo-$id"))))
            put(
                "ExternalUrls",
                JsonArray(
                    listOf(
                        urlEntry("TheMovieDb", "https://www.themoviedb.org/${if (type == "Series") "tv" else "movie"}/1"),
                        urlEntry("IMDb", "https://www.imdb.com/title/tt0000001/"),
                        urlEntry("Studio", "https://example.com/demo-pictures"),
                    ),
                ),
            )
            put("Tags", JsonArray(listOf("Slow burn", "Atmospheric").map(::JsonPrimitive)))
        }
    }

private fun urlEntry(
    name: String,
    url: String,
) = buildJsonObject {
    put("Name", name)
    put("Url", url)
}
