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

/** The end of a log: [entries], oldest first, and whether the file was longer than what was kept. */
data class LogText(
    val entries: List<LogEntry>,
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
 * The last [maxBytes] of the log [name] as entries, with anything that looks like a credential blanked first (see
 * [redactLog]): Jellyfin's own logs can carry an `api_key` in a request URL, and what this returns is shown, copied
 * and shared. [ownToken], the signed-in user's, is blanked wherever it appears.
 */
suspend fun JellyfinClient.logTail(
    name: String,
    ownToken: String? = null,
    maxBytes: Int = LOG_TAIL_BYTES,
): LogText {
    val tail = getTail("System/Logs/Log", mapOf("name" to name), maxBytes)
    return LogText(parseLogEntries(redactLog(tail.text, ownToken)), tail.truncated)
}

/** How much of a log is kept: enough for a day's errors, small enough for a phone. */
const val LOG_TAIL_BYTES = 512 * 1024

/** A key word and its value: `api_key=…`, `ApiKey: …`, `Token="…"`, `access token "…"`, `secret "…"`, or a bare 32-digit hex after it. */
private val SECRET =
    Regex(
        """(?i)\b(api[_-]?key|access[_-]?token|token|secret)\b("?\s*[=:]\s*"?|\s+"|\s+(?=[0-9a-f]{32}\b))([A-Za-z0-9._~+/=-]{8,})""",
    )

/** A 32-digit hex (what Jellyfin makes access tokens and API keys of) within a few words after a key word. */
private val HEX_AFTER_KEY =
    Regex("""(?i)\b((?:api[ _-]?key|access[ _-]?token|token|secret)\b[^\n]{0,$NEAR_CHARS}?)\b[0-9a-f]{32}\b""")

/**
 * [line] with credentials replaced by `[redacted]`. Jellyfin's own log statements that print one, read
 * from its source: `Logging out access token "…"`, `Authenticated with Schedules Direct token: "…"`,
 * Quick Connect's `Secret "…" already expired`, and the `api_key=` in a request URL. A JSON
 * `"AccessToken":"…"` counts too, and so does a 32-digit hex within a few words after a key word, for a
 * wording not seen yet. Over-redacting is the safe side: this is shown and shared.
 */
fun redactSecrets(line: String): String {
    val shaped = SECRET.replace(line) { "${it.groupValues[1]}${it.groupValues[2]}$REDACTED" }
    return HEX_AFTER_KEY.replace(shaped) { "${it.groupValues[1]}$REDACTED" }
}

private const val NEAR_CHARS = 60

internal const val REDACTED = "[redacted]"

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
