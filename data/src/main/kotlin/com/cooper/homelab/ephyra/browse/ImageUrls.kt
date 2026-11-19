package com.cooper.homelab.ephyra.browse

import com.cooper.homelab.ephyra.server.JellyfinClient

/**
 * The URL of an item's picture at a size. A default Jellyfin server serves pictures without a token (its OpenAPI document
 * gives the image routes no security requirement), so the URL carries none and never will: the access token goes only
 * where `CLAUDE.md` says it may. A picture is asked for at the size the screen draws it, so a grid does not pull full-size art.
 */
class ImageUrls(
    private val client: JellyfinClient,
) {
    /** [kind] of [item] at no more than [maxWidth] by [maxHeight] pixels, or null when the item has no such picture. */
    fun of(
        item: BrowseItem,
        kind: ImageKind,
        maxWidth: Int,
        maxHeight: Int? = null,
    ): String? = item.imageTags[kind]?.let { of(item.id, kind, it, maxWidth, maxHeight) }

    /** The picture [tag] names, for any item id: the tag is the cache key, so a changed picture is a new URL. */
    fun of(
        itemId: String,
        kind: ImageKind,
        tag: String,
        maxWidth: Int,
        maxHeight: Int? = null,
    ): String {
        val query = linkedMapOf("tag" to tag, "maxWidth" to maxWidth.toString())
        maxHeight?.let { query["maxHeight"] = it.toString() }
        return client.url("Items/$itemId/Images/${kind.serverName}", query).toString()
    }
}
