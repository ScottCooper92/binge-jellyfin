package com.cooper.homelab.ephyra.demo

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TICKS_PER_SECOND = 10_000_000L
private const val INTRO_START_SECONDS = 30L
private const val INTRO_END_SECONDS = 90L
private const val CREDITS_SECONDS = 120L

/**
 * The stretches the demo marks in a title, as `GET /MediaSegments/{itemId}` answers: an intro near the start of an episode and
 * the credits for the last two minutes of an episode or a film. A title the demo does not have has none.
 */
internal fun segmentsOf(title: DemoTitle?): DemoAnswer {
    val runtime = title?.runtimeTicks ?: 0L
    val items =
        buildJsonArray {
            if (title?.type ==
                "Episode"
            ) {
                add(segment(title.id, "Intro", INTRO_START_SECONDS * TICKS_PER_SECOND, INTRO_END_SECONDS * TICKS_PER_SECOND))
            }
            if (title != null && (title.type == "Episode" || title.type == "Movie") && runtime > CREDITS_SECONDS * TICKS_PER_SECOND) {
                add(segment(title.id, "Outro", runtime - CREDITS_SECONDS * TICKS_PER_SECOND, runtime))
            }
        }
    return ok(
        buildJsonObject {
            put("Items", items)
            put("TotalRecordCount", items.size)
            put("StartIndex", 0)
        },
    )
}

private fun segment(
    itemId: String,
    type: String,
    start: Long,
    end: Long,
) = buildJsonObject {
    put("Id", "$itemId-$type")
    put("ItemId", itemId)
    put("Type", type)
    put("StartTicks", start)
    put("EndTicks", end)
}
