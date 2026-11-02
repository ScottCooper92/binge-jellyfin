package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The keys of the server's configuration (`/System/Configuration`) that the Advanced page edits, as the server names them. */
internal const val ACTIVITY_LOG_RETENTION_DAYS = "ActivityLogRetentionDays"
internal const val LIBRARY_MONITOR_DELAY = "LibraryMonitorDelay"
internal const val LIBRARY_UPDATE_DURATION = "LibraryUpdateDuration"
internal const val LIBRARY_SCAN_CONCURRENCY = "LibraryScanFanoutConcurrency"
internal const val PARALLEL_IMAGE_ENCODING_LIMIT = "ParallelImageEncodingLimit"
internal const val IMAGE_EXTRACTION_TIMEOUT_MS = "ImageExtractionTimeoutMs"
internal const val ENABLE_SLOW_RESPONSE_WARNING = "EnableSlowResponseWarning"
internal const val SLOW_RESPONSE_THRESHOLD_MS = "SlowResponseThresholdMs"
internal const val ENABLE_METRICS = "EnableMetrics"
internal const val ALLOW_CLIENT_LOG_UPLOAD = "AllowClientLogUpload"
internal const val CORS_HOSTS = "CorsHosts"
internal const val INACTIVE_SESSION_THRESHOLD = "InactiveSessionThreshold"
internal const val CAST_RECEIVER_APPLICATIONS = "CastReceiverApplications"
internal const val CACHE_SIZE = "CacheSize"
internal const val ENABLE_LEGACY_AUTHORIZATION = "EnableLegacyAuthorization"

/** A Google Cast receiver application the server offers to clients: its id and the name it shows. */
data class CastReceiver(
    val id: String,
    val name: String,
)

/**
 * The server's advanced options, read from its configuration. A server leaves out the options its release doesn't
 * have, so each is null when absent and the page shows only the rows for [present] keys. [activityLogRetentionDays]
 * is null both when the server leaves it out and when it sends null, which the server's own cleanup task cannot run with.
 */
data class AdvancedSettings(
    val present: Set<String> = emptySet(),
    val activityLogRetentionDays: Int? = null,
    val libraryMonitorDelay: Int = 0,
    val libraryUpdateDuration: Int = 0,
    val libraryScanConcurrency: Int = 0,
    val parallelImageEncodingLimit: Int = 0,
    val imageExtractionTimeoutMs: Int = 0,
    val slowResponseWarning: Boolean = false,
    val slowResponseThresholdMs: Long = 0,
    val metrics: Boolean = false,
    val allowClientLogUpload: Boolean = false,
    val corsHosts: List<String> = emptyList(),
    val inactiveSessionThreshold: Int = 0,
    val castReceivers: List<CastReceiver> = emptyList(),
    val cacheSize: Int = 0,
    val legacyAuthorization: Boolean = false,
) {
    /** Whether the server sent [key], so it has the option. */
    fun has(key: String): Boolean = key in present
}

fun JsonObject.advancedSettings(): AdvancedSettings =
    AdvancedSettings(
        present = keys.filter { it in ADVANCED_KEYS }.toSet(),
        activityLogRetentionDays = int(ACTIVITY_LOG_RETENTION_DAYS),
        libraryMonitorDelay = int(LIBRARY_MONITOR_DELAY) ?: 0,
        libraryUpdateDuration = int(LIBRARY_UPDATE_DURATION) ?: 0,
        libraryScanConcurrency = int(LIBRARY_SCAN_CONCURRENCY) ?: 0,
        parallelImageEncodingLimit = int(PARALLEL_IMAGE_ENCODING_LIMIT) ?: 0,
        imageExtractionTimeoutMs = int(IMAGE_EXTRACTION_TIMEOUT_MS) ?: 0,
        slowResponseWarning = bool(ENABLE_SLOW_RESPONSE_WARNING) == true,
        slowResponseThresholdMs = long(SLOW_RESPONSE_THRESHOLD_MS) ?: 0,
        metrics = bool(ENABLE_METRICS) == true,
        allowClientLogUpload = bool(ALLOW_CLIENT_LOG_UPLOAD) == true,
        corsHosts = strings(CORS_HOSTS),
        inactiveSessionThreshold = int(INACTIVE_SESSION_THRESHOLD) ?: 0,
        castReceivers = array(CAST_RECEIVER_APPLICATIONS).mapNotNull { it.castReceiver() },
        cacheSize = int(CACHE_SIZE) ?: 0,
        legacyAuthorization = bool(ENABLE_LEGACY_AUTHORIZATION) == true,
    )

private fun JsonElement.castReceiver(): CastReceiver? {
    val json = this as? JsonObject ?: return null
    val id = json.string("Id")?.takeIf(String::isNotBlank) ?: return null
    return CastReceiver(id, json.string("Name").orEmpty())
}

/** [list] as the JSON the server reads for [CORS_HOSTS]. */
internal fun corsHostsJson(list: List<String>): JsonArray = JsonArray(list.map(::JsonPrimitive))

/** [list] as the JSON the server reads for [CAST_RECEIVER_APPLICATIONS]: both `Id` and `Name` are required there. */
internal fun castReceiversJson(list: List<CastReceiver>): JsonArray =
    JsonArray(list.map { JsonObject(mapOf("Id" to JsonPrimitive(it.id), "Name" to JsonPrimitive(it.name))) })

private val ADVANCED_KEYS =
    setOf(
        ACTIVITY_LOG_RETENTION_DAYS,
        LIBRARY_MONITOR_DELAY,
        LIBRARY_UPDATE_DURATION,
        LIBRARY_SCAN_CONCURRENCY,
        PARALLEL_IMAGE_ENCODING_LIMIT,
        IMAGE_EXTRACTION_TIMEOUT_MS,
        ENABLE_SLOW_RESPONSE_WARNING,
        SLOW_RESPONSE_THRESHOLD_MS,
        ENABLE_METRICS,
        ALLOW_CLIENT_LOG_UPLOAD,
        CORS_HOSTS,
        INACTIVE_SESSION_THRESHOLD,
        CAST_RECEIVER_APPLICATIONS,
        CACHE_SIZE,
        ENABLE_LEGACY_AUTHORIZATION,
    )
