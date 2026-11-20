package com.cooper.homelab.ephyra.server

import com.binge.companion.contracts.library.v1.Trickplay
import com.binge.companion.sdk.problem

private const val INDEX = "TRICKPLAYINDEX"

/** The width the seek bar wants: the server's own default. */
const val WANTED_TRICKPLAY_WIDTH = 320

/** The smallest set at least [WANTED_TRICKPLAY_WIDTH] wide, else the widest there is; null when there are none. */
fun chooseTrickplay(sets: List<TrickplayInfo>): TrickplayInfo? =
    sets.filter { it.width >= WANTED_TRICKPLAY_WIDTH }.minByOrNull { it.width } ?: sets.maxByOrNull { it.width }

/**
 * The address of a set's sheets, with one `{index}` where the sheet's number goes (`GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg`).
 * [itemId] is the item the media source is: an alternate version is an item of its own, and the route reads its sheets from the item the
 * path names. The route's `mediaSourceId` query is read for the HLS playlist only, so the address carries none. It names no credential:
 * the source's `Authorization` header carries it, as it does for the stream.
 */
fun JellyfinClient.trickplayTemplate(
    itemId: String,
    width: Int,
): String = url("Videos/$itemId/Trickplay/$width/$INDEX.jpg").toString().replace(INDEX, "{index}")

/**
 * The seek-thumbnail descriptor for one media source's [sets], as the contract states it, or null: none to choose from, or a descriptor
 * over the contract's bounds, which a player would drop whole. Both are normal, never an error. [itemId] is the media source's.
 */
fun JellyfinClient.trickplayDescriptor(
    itemId: String,
    sets: List<TrickplayInfo>,
): Trickplay? {
    val chosen = chooseTrickplay(sets) ?: return null
    return Trickplay
        .newBuilder()
        .setUrlTemplate(trickplayTemplate(itemId, chosen.width))
        .setThumbnailWidth(chosen.width)
        .setThumbnailHeight(chosen.height)
        .setTileColumns(chosen.tileWidth)
        .setTileRows(chosen.tileHeight)
        .setIntervalMillis(chosen.intervalMillis)
        .setThumbnailCount(chosen.thumbnailCount)
        .build()
        .takeIf { it.problem() == null }
}
