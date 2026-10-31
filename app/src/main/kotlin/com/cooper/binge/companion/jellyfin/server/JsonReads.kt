package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Jellyfin's answers are read as JsonElement trees, never through @Serializable classes: a field the
// server adds or drops reads as absent rather than failing the whole answer.

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.array(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
