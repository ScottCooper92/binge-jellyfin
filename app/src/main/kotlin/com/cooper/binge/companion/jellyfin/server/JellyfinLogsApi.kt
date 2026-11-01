package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okio.BufferedSource

/** One of the server's log files. Admin only. */
data class LogFile(
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long?,
)

/** The end of a log: [lines], oldest first, and whether the file was longer than what was kept. */
data class LogText(
    val lines: List<String>,
    val truncated: Boolean,
)

/** The last bytes of a text answer, and whether there were more before them. */
internal data class TextTail(
    val text: String,
    val truncated: Boolean,
)

/** The server's log files, newest first. */
suspend fun JellyfinClient.logFiles(): List<LogFile> =
    (get("System/Logs") as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonObject)?.logFile() }
        .sortedByDescending { it.modifiedMillis ?: Long.MIN_VALUE }

private fun JsonObject.logFile(): LogFile? {
    val name = string("Name") ?: return null
    return LogFile(name, long("Size") ?: 0, string("DateModified")?.epochMillisOrNull())
}

/**
 * The last [maxBytes] of the log [name], with anything that looks like a credential blanked (see
 * [redactSecrets]): Jellyfin's own logs can carry an `api_key` in a request URL, and what this returns
 * is shown and shared.
 */
suspend fun JellyfinClient.logTail(
    name: String,
    maxBytes: Int = LOG_TAIL_BYTES,
): LogText {
    val tail = getTail("System/Logs/Log", mapOf("name" to name), maxBytes)
    return LogText(
        tail.text
            .lines()
            .dropLastWhile { it.isEmpty() }
            .map(::redactSecrets),
        tail.truncated,
    )
}

/** How much of a log is kept: enough for a day's errors, small enough for a phone. */
const val LOG_TAIL_BYTES = 512 * 1024

private val SECRET =
    Regex(
        """(?i)\b(api[_-]?key|access[_-]?token|token)\b("?\s*[=:]\s*"?|\s+"|\s+(?=[0-9a-f]{32}\b))([A-Za-z0-9._~+/=-]{8,})""",
    )

/** [line] with the value after `api_key=`, `ApiKey:`, `Token="…"`, a quoted `access token "…"` or a bare 32-digit hex after `token`, and the like replaced by `[redacted]`; a JSON `"AccessToken":"…"` counts too. */
fun redactSecrets(line: String): String = SECRET.replace(line) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }

/** Reads [source] to its end, keeping the last [maxBytes] of it. A cut-off first line is dropped. */
internal fun readTail(
    source: BufferedSource,
    maxBytes: Int,
): TextTail {
    val ring = ByteArray(maxBytes)
    val chunk = ByteArray(CHUNK)
    var total = 0L
    while (true) {
        val read = source.read(chunk)
        if (read < 0) break
        var copied = 0
        while (copied < read) {
            val at = (total % maxBytes).toInt()
            val run = minOf(read - copied, maxBytes - at)
            System.arraycopy(chunk, copied, ring, at, run)
            copied += run
            total += run
        }
    }
    if (total <= maxBytes) return TextTail(String(ring, 0, total.toInt(), Charsets.UTF_8), truncated = false)
    val start = (total % maxBytes).toInt()
    val ordered = ring.copyOfRange(start, maxBytes) + ring.copyOfRange(0, start)
    val text = String(ordered, Charsets.UTF_8)
    return TextTail(text.substringAfter('\n', missingDelimiterValue = text), truncated = true)
}

private const val CHUNK = 8192
