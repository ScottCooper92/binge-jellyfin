package com.cooper.homelab.ephyra.demo

import com.cooper.homelab.ephyra.server.TICKS_PER_SECOND
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val INTRO_START_TICKS = 30L * TICKS_PER_SECOND
private const val INTRO_END_TICKS = 90L * TICKS_PER_SECOND
private const val CREDITS_SECONDS = 120L

/**
 * The stretches the demo marks in a title, as `GET /MediaSegments/{itemId}` answers: an intro near the start of an episode and
 * the credits for the last two minutes of an episode or a film. A title the demo does not have has none.
 */
internal fun segmentsOf(title: DemoTitle?): DemoAnswer {
    val items =
        buildJsonArray {
            if (title?.isEpisode() == true) add(segment(title.id, "Intro", INTRO_START_TICKS, INTRO_END_TICKS))
            if (title != null && title.hasCredits()) {
                add(segment(title.id, "Outro", title.runtimeTicks - CREDITS_SECONDS * TICKS_PER_SECOND, title.runtimeTicks))
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

private fun DemoTitle.isEpisode(): Boolean = type == "Episode"

private fun DemoTitle.hasCredits(): Boolean = (type == "Episode" || type == "Movie") && runtimeTicks > CREDITS_SECONDS * TICKS_PER_SECOND

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
