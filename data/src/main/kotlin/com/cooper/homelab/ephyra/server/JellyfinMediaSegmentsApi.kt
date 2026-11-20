package com.cooper.homelab.ephyra.server

import kotlinx.serialization.json.JsonObject

/** What a stretch of a title is, as `MediaSegmentType` names it. [Other] covers `Commercial`, `Unknown` and any type added later. */
enum class SegmentKind { Intro, Recap, Preview, Outro, Other }

/** One stretch of an item the server has marked, from [startMillis] to [endMillis]. */
data class MediaSegment(
    val kind: SegmentKind,
    val startMillis: Long,
    val endMillis: Long,
)

/**
 * The stretches the server has marked in an item (`GET /MediaSegments/{itemId}`, 10.10 on, `profile.hasMediaSegments`): intros, recaps,
 * previews and credits. An entry with no type, no times, or no length is dropped, and the rest come in the order they start.
 */
suspend fun JellyfinClient.mediaSegments(itemId: String): List<MediaSegment> =
    getObject("MediaSegments/$itemId", emptyMap())
        .array("Items")
        .filterIsInstance<JsonObject>()
        .mapNotNull { it.toSegment() }
        .sortedBy { it.startMillis }

private fun JsonObject.toSegment(): MediaSegment? {
    val start = long("StartTicks") ?: return null
    val end = long("EndTicks") ?: return null
    if (end <= start) return null
    val kind =
        when (string("Type")) {
            "Intro" -> SegmentKind.Intro
            "Recap" -> SegmentKind.Recap
            "Preview" -> SegmentKind.Preview
            "Outro" -> SegmentKind.Outro
            else -> SegmentKind.Other
        }
    return MediaSegment(kind, start / TICKS_PER_MILLI, end / TICKS_PER_MILLI)
}
