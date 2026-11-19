package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/**
 * Titles matching [term]: `/Items` with `searchTerm`, over every library the user can see. The term is a query value, so
 * the client encodes it (a space, `&`, `+`, `%`, `/` and non-ASCII all arrive as typed). The order is the server's.
 */
internal suspend fun JellyfinClient.browseSearchTitles(
    userId: String,
    term: String,
    types: List<String>,
    startIndex: Int,
    limit: Int,
    withTotal: Boolean,
): Page<BrowseItem> {
    val query = cardQuery(userId)
    query["searchTerm"] = term
    query["recursive"] = "true"
    query["includeItemTypes"] = types.joinToString(",")
    query["startIndex"] = startIndex.toString()
    query["limit"] = limit.toString()
    query["enableTotalRecordCount"] = withTotal.toString()
    return decodeAnswer(ItemsWire.serializer(), getObject("Items", query), "Items").toPage(startIndex, withTotal)
}

/** People matching [term]: `/Persons`, which pages only from 12.0 (see [JellyfinServerProfile.hasPersonsPaging]). */
internal suspend fun JellyfinClient.browseSearchPeople(
    userId: String,
    profile: JellyfinServerProfile,
    term: String,
    startIndex: Int,
    limit: Int,
): Page<PersonResult> {
    val query =
        linkedMapOf(
            "userId" to userId,
            "searchTerm" to term,
            "limit" to limit.toString(),
            "enableUserData" to "false",
            "enableImages" to "true",
            "enableImageTypes" to "Primary",
            "imageTypeLimit" to "1",
        )
    if (profile.hasPersonsPaging) query["startIndex"] = startIndex.toString()
    val items = decodeAnswer(ItemsWire.serializer(), getObject("Persons", query), "Persons").items
    val people =
        items.mapNotNull { person ->
            person.id?.let { PersonResult(it, person.name.orEmpty(), person.imageTags["Primary"]) }
        }
    return Page(people, null, startIndex)
}
