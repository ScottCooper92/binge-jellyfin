package com.cooper.binge.companion.jellyfin.server

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A log line's severity, lowest first. */
enum class LogLevel { Debug, Info, Warn, Error }

/**
 * One entry of a Jellyfin log: a header line `[timestamp] [LVL] [thread] Logger: message` and the unbracketed lines
 * after it (a stack trace) folded into [message]. [level] is null for text that has no header of its own. [text] is the
 * entry as the file has it, already redacted, and is what copying and sharing put out.
 */
data class LogEntry(
    val id: String,
    val timestampRaw: String?,
    val timestampMillis: Long?,
    val level: LogLevel?,
    val logger: String?,
    val message: String,
    val text: String,
)

/**
 * Log text with credentials already blanked. It can only be made by [redactLog], and [parseLogEntries] takes nothing
 * else, so no entry exists that was not redacted first.
 */
@JvmInline
value class RedactedText internal constructor(
    val value: String,
)

/**
 * [raw] with every credential blanked, as one text, before it is split into lines: a secret that sits across a line
 * break is still seen. [ownToken], the signed-in user's, is blanked wherever it appears.
 */
fun redactLog(
    raw: String,
    ownToken: String? = null,
): RedactedText = RedactedText(redactSecrets(raw.withoutToken(ownToken)))

/** The entries of [text], oldest first. Text before the first header, or that fits no header, is kept as an unlabelled entry, never dropped. */
fun parseLogEntries(text: RedactedText): List<LogEntry> {
    val pending = mutableListOf<MutableList<String>>()
    text.value.lines().dropLastWhile { it.isEmpty() }.forEach { line ->
        if (HEADER.containsMatchIn(line) || pending.isEmpty()) pending += mutableListOf(line) else pending.last() += line
    }
    return pending.mapIndexed { index, lines -> entryOf(index.toString(), lines) }
}

private fun entryOf(
    id: String,
    lines: List<String>,
): LogEntry {
    val text = lines.joinToString("\n")
    val header =
        HEADER.find(lines.first())
            ?: return LogEntry(id, null, null, null, null, text, text)
    val (timestamp, code, rest) = header.destructured
    val named = LOGGER.find(rest)
    val logger = named?.groupValues?.get(1)
    val message = named?.groupValues?.get(2) ?: rest
    return LogEntry(
        id = id,
        timestampRaw = timestamp,
        timestampMillis = timestamp.epochMillisOrNullLog(),
        level = levelOf(code),
        logger = logger,
        message = (listOf(message) + lines.drop(1)).joinToString("\n"),
        text = text,
    )
}

private fun levelOf(code: String): LogLevel =
    when (code) {
        "VRB", "DBG" -> LogLevel.Debug
        "WRN" -> LogLevel.Warn
        "ERR", "FTL" -> LogLevel.Error
        else -> LogLevel.Info
    }

private fun String.epochMillisOrNullLog(): Long? =
    try {
        OffsetDateTime.parse(this, TIMESTAMP).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS] xxx")

/** `[2026-11-01 10:00:00.123 +00:00] [INF] [12] ` then the rest; the thread is optional. */
private val HEADER = Regex("""^\[(\d{4}-\d{2}-\d{2} [^\]]+)] \[(VRB|DBG|INF|WRN|ERR|FTL)](?: \[[^\]]*])? ?(.*)$""")

/** A logger name, then a colon and a space: `Emby.Server.ApplicationHost: Message`. */
private val LOGGER = Regex("""^([\w.+`<>-]+): (.*)$""", RegexOption.DOT_MATCHES_ALL)

/** [this] with [token], this app's own, replaced wherever it appears: exact, whatever the wording around it. */
private fun String.withoutToken(token: String?): String = token?.takeIf { it.isNotBlank() }?.let { replace(it, REDACTED) } ?: this
