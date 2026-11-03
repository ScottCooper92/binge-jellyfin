package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonObject

/**
 * One set of seek thumbnails the server holds for a media source, at one [width]: a sheet is a [tileWidth] by [tileHeight]
 * grid of [width] by [height] pixel frames, [intervalMillis] apart, [thumbnailCount] in all. From the item's `Trickplay`.
 */
data class TrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val intervalMillis: Long,
)

/**
 * The item's `Trickplay`: media source id, then width, then the set. A set that leaves out a field is dropped, as one
 * the server did not write, and so is a source left with none.
 */
internal fun JsonObject.trickplayOf(): Map<String, List<TrickplayInfo>> =
    obj("Trickplay")
        ?.mapNotNull { (sourceId, sets) ->
            val infos = (sets as? JsonObject)?.values.orEmpty().mapNotNull { (it as? JsonObject)?.toTrickplayInfo() }
            if (infos.isEmpty()) null else sourceId to infos
        }?.toMap()
        .orEmpty()

private fun JsonObject.toTrickplayInfo(): TrickplayInfo? {
    val sizes = listOf("Width", "Height", "TileWidth", "TileHeight", "ThumbnailCount").map { int(it) ?: return null }
    val intervalMillis = long("Interval") ?: return null
    return TrickplayInfo(sizes[WIDTH], sizes[HEIGHT], sizes[TILE_WIDTH], sizes[TILE_HEIGHT], sizes[COUNT], intervalMillis)
}

private const val WIDTH = 0
private const val HEIGHT = 1
private const val TILE_WIDTH = 2
private const val TILE_HEIGHT = 3
private const val COUNT = 4
