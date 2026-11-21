package com.cooper.homelab.ephyra.openitem

import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.browse.ItemKind
import com.cooper.homelab.ephyra.browse.TitleRepository
import com.cooper.homelab.ephyra.server.IndexKey
import com.cooper.homelab.ephyra.server.IndexType
import com.cooper.homelab.ephyra.server.JellyfinItem

/**
 * Finds where [request] leads on the signed-in server at [serverBaseUrl], in this order:
 * 1. the item id, only when the caller's server address is this server's, and only for a movie or a series the user can see (and, when
 *    the caller named a TMDB type, of that type), read through [titles];
 * 2. the TMDB id on this server, through [find] (the library index), whatever address the caller gave;
 * 3. [OpenTarget.Home].
 * A read that fails is no match, so a server that cannot be reached opens the home and never an error.
 */
suspend fun resolveOpenTarget(
    request: OpenItemRequest,
    serverBaseUrl: String,
    titles: TitleRepository,
    find: suspend (IndexKey) -> JellyfinItem?,
): OpenTarget {
    val sameServer = request.serverKey != null && request.serverKey == serverKey(serverBaseUrl, assumeHttp = true)
    val byId = request.itemId?.takeIf { sameServer }?.let { titleById(it, request.tmdb?.type, titles) }
    return byId ?: request.tmdb?.let { titleByTmdb(it, find) } ?: OpenTarget.Home
}

private suspend fun titleById(
    id: String,
    expected: IndexType?,
    titles: TitleRepository,
): OpenTarget.Title? {
    val kind = attempt { titles.title(id) }.getOrNull()?.item?.kind ?: return null
    val type = indexTypeOf(kind) ?: return null
    return OpenTarget.Title(kind, id).takeIf { expected == null || expected == type }
}

private suspend fun titleByTmdb(
    ref: TmdbRef,
    find: suspend (IndexKey) -> JellyfinItem?,
): OpenTarget.Title? {
    val item = attempt { find(IndexKey(ref.type, ref.id)) }.getOrNull() ?: return null
    return OpenTarget.Title(if (ref.type == IndexType.MOVIE) ItemKind.Movie else ItemKind.Series, item.id)
}

/** The TMDB type a movie or a series answers to; the other kinds have none, and the exported entry opens neither. */
private fun indexTypeOf(kind: ItemKind): IndexType? =
    when (kind) {
        ItemKind.Movie -> IndexType.MOVIE
        ItemKind.Series -> IndexType.SERIES
        else -> null
    }
