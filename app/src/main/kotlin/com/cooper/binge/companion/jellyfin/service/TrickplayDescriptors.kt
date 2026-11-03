package com.cooper.binge.companion.jellyfin.service

import com.binge.companion.contracts.library.v1.Trickplay
import com.binge.companion.sdk.problem
import com.cooper.binge.companion.jellyfin.server.JellyfinClient
import com.cooper.binge.companion.jellyfin.server.TrickplayInfo

private const val INDEX = "TRICKPLAYINDEX"

/**
 * The seek-thumbnail descriptor for a playback source (`library.proto` > `Trickplay`), or none: no trickplay, a server
 * that has none, and a descriptor the contract's bounds would have the host drop are all normal, never an error.
 */
internal object TrickplayDescriptors {
    /** The width the seek bar wants: the server's own default, since the contract has the companion pick. */
    const val WANTED_WIDTH = 320

    /** The smallest set at least [WANTED_WIDTH] wide, else the widest there is. */
    fun choose(sets: List<TrickplayInfo>): TrickplayInfo? =
        sets.filter { it.width >= WANTED_WIDTH }.minByOrNull { it.width } ?: sets.maxByOrNull { it.width }

    /**
     * The descriptor for [sets], the item's trickplay for one media source. [mediaSourceId] is also the item id in the path:
     * an alternate version is an item of its own, and its sheets are under its id. The template names no credential: the
     * source's `Authorization` header carries it, as it does for the stream.
     */
    fun descriptor(
        client: JellyfinClient,
        mediaSourceId: String,
        sets: List<TrickplayInfo>,
    ): Trickplay? {
        val chosen = choose(sets) ?: return null
        val template =
            client
                .url("Videos/$mediaSourceId/Trickplay/${chosen.width}/$INDEX.jpg", mapOf("mediaSourceId" to mediaSourceId))
                .toString()
                .replace(INDEX, "{index}")
        val descriptor =
            Trickplay
                .newBuilder()
                .setUrlTemplate(template)
                .setThumbnailWidth(chosen.width)
                .setThumbnailHeight(chosen.height)
                .setTileColumns(chosen.tileWidth)
                .setTileRows(chosen.tileHeight)
                .setIntervalMillis(chosen.intervalMillis)
                .setThumbnailCount(chosen.thumbnailCount)
                .build()
        return descriptor.takeIf { it.problem() == null }
    }
}
