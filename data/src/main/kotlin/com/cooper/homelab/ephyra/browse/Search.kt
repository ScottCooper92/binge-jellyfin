package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient
import com.cooper.homelab.ephyra.server.JellyfinServerProfile

/** A person in a search result: their item id (never their name), name and picture tag. */
data class PersonResult(
    val id: String,
    val name: String,
    val imageTag: String?,
)

/**
 * Search over the user's own server. Failures are the transport's, as in [LibraryRepository]. A term is trimmed; a blank
 * one answers an empty page with no request. The server documents no minimum length, so none is imposed beyond that.
 * A search that is cancelled (the term changed mid-load) cancels its request.
 */
interface SearchRepository {
    /** Titles whose name matches [term], in the server's order, as cards. */
    suspend fun titles(
        term: String,
        kinds: Set<ItemKind> = DEFAULT_KINDS,
        startIndex: Int = 0,
        limit: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
        withTotal: Boolean = false,
    ): Page<BrowseItem>

    /**
     * People whose name matches [term]. Before 12.0 the server cannot page them: [startIndex] past 0 is an empty page there,
     * and [limit] is all there is.
     */
    suspend fun people(
        term: String,
        startIndex: Int = 0,
        limit: Int = ItemsRepository.DEFAULT_PAGE_SIZE,
    ): Page<PersonResult>

    companion object {
        val DEFAULT_KINDS = setOf(ItemKind.Movie, ItemKind.Series, ItemKind.Episode)
    }
}

/** [SearchRepository] over the user's own server. */
class JellyfinSearchRepository(
    private val client: JellyfinClient,
    private val userId: String,
    private val profile: JellyfinServerProfile,
) : SearchRepository {
    override suspend fun titles(
        term: String,
        kinds: Set<ItemKind>,
        startIndex: Int,
        limit: Int,
        withTotal: Boolean,
    ): Page<BrowseItem> {
        val trimmed = term.trim()
        val types = kinds.mapNotNull { it.serverName() }
        if (trimmed.isEmpty() || types.isEmpty()) return Page(emptyList(), if (withTotal) 0 else null, startIndex)
        return client.browseSearchTitles(userId, trimmed, types, startIndex, limit, withTotal)
    }

    override suspend fun people(
        term: String,
        startIndex: Int,
        limit: Int,
    ): Page<PersonResult> {
        val trimmed = term.trim()
        if (trimmed.isEmpty() || (startIndex > 0 && !profile.hasPersonsPaging)) return Page(emptyList(), null, startIndex)
        return client.browseSearchPeople(userId, profile, trimmed, startIndex, limit)
    }
}

private fun ItemKind.serverName(): String? = if (this == ItemKind.Other) null else name
