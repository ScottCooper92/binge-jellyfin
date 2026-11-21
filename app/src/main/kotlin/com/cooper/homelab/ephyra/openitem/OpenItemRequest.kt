package com.cooper.homelab.ephyra.openitem

import com.binge.companion.sdk.attempt
import com.cooper.homelab.ephyra.server.IndexType
import java.net.URI

/** A TMDB title, as the caller of the exported entry names it: a movie or a TV series, and its TMDB id. */
data class TmdbRef(
    val type: IndexType,
    val id: Int,
)

/**
 * What another app asked this one to open, after every part of it was checked. The caller's intent is untrusted, so each field here is
 * either valid or null, and a request with nothing to open is not made at all ([parse] answers null).
 *
 * [serverKey] is the caller's server address reduced to what two spellings of one address share (see [serverKey]); it is only ever
 * compared with the signed-in server's address, and never requested. [itemId] is a Jellyfin item id in the server's own spelling.
 */
data class OpenItemRequest(
    val serverKey: String?,
    val itemId: String?,
    val tmdb: TmdbRef?,
) {
    companion object {
        /**
         * The request the four raw parts make, or null when none of them names a title: a missing, mistyped or malformed part is dropped
         * on its own, so a request with a bad id and a good TMDB id still opens by TMDB id.
         */
        fun parse(
            serverUrl: String?,
            itemId: String?,
            tmdbType: String?,
            tmdbId: Int?,
        ): OpenItemRequest? =
            OpenItemRequest(
                serverKey = serverUrl?.let { serverKey(it) },
                itemId = itemId?.let(::itemIdOf),
                tmdb = tmdbRefOf(tmdbType, tmdbId),
            ).takeIf { it.itemId != null || it.tmdb != null }
    }
}

/** The longest server address or item id spelling worth looking at; anything longer is not one. */
private const val MAX_ADDRESS_CHARS = 2048
private const val MAX_ITEM_ID_CHARS = 36
private const val ITEM_ID_HEX_DIGITS = 32
private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443

/**
 * [raw] as the key two spellings of one server address share: its scheme, its host in lower case, its port (the scheme's own when none is
 * written), and its path without a trailing slash. A query, a fragment and any user info are not part of it. Null when [raw] is not an
 * http or https address. With [assumeHttp] an address with no scheme is read as http, as the app's own setup reads a typed one.
 */
fun serverKey(
    raw: String,
    assumeHttp: Boolean = false,
): String? {
    val text = raw.trim()
    val withScheme = if ("://" in text) text else "http://$text".takeIf { assumeHttp }
    val uri = withScheme?.takeIf { text.isNotEmpty() && it.length <= MAX_ADDRESS_CHARS }?.let { attempt { URI(it) }.getOrNull() }
    val scheme = uri?.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
    val host = uri?.host?.lowercase()?.takeIf { it.isNotEmpty() }
    return if (uri == null || scheme == null || host == null) {
        null
    } else {
        val port = uri.port.takeIf { it != -1 } ?: if (scheme == "https") HTTPS_PORT else HTTP_PORT
        "$scheme://$host:$port${uri.rawPath.orEmpty().trimEnd('/')}"
    }
}

/** A Jellyfin item id in the spelling the server sends (32 hex digits, lower case), from [raw] with or without its dashes; null if it is not one. */
private fun itemIdOf(raw: String): String? =
    raw
        .trim()
        .takeIf { it.length <= MAX_ITEM_ID_CHARS }
        ?.replace("-", "")
        ?.takeIf { id -> id.length == ITEM_ID_HEX_DIGITS && id.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } }
        ?.lowercase()

private fun tmdbRefOf(
    type: String?,
    id: Int?,
): TmdbRef? {
    val indexType =
        when (type?.trim()?.lowercase()) {
            "movie" -> IndexType.MOVIE
            "tv" -> IndexType.SERIES
            else -> null
        }
    return if (indexType != null && id != null && id > 0) TmdbRef(indexType, id) else null
}
