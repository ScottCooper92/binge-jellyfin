package com.cooper.binge.companion.jellyfin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HEX = "0123456789abcdef0123456789abcdef"
private const val STAMP = "[2026-10-31 11:59:41.871 +00:00]"

class LogEntriesTest {
    private fun parse(
        raw: String,
        token: String? = null,
    ) = parseLogEntries(redactLog(raw, token))

    @Test
    fun `a header line becomes an entry with its time, level, logger and message`() {
        val entry = parse("$STAMP [WRN] [18] Jellyfin.Api.Auth: Slow query took 4.2 s\n").single()

        assertEquals("2026-10-31 11:59:41.871 +00:00", entry.timestampRaw)
        assertEquals(1_793_447_981_871L, entry.timestampMillis)
        assertEquals(LogLevel.Warn, entry.level)
        assertEquals("Jellyfin.Api.Auth", entry.logger)
        assertEquals("Slow query took 4.2 s", entry.message)
    }

    @Test
    fun `each level code maps to a level, and the thread is optional`() {
        val levels = listOf("VRB", "DBG", "INF", "WRN", "ERR", "FTL").map { parse("$STAMP [$it] A: x").single().level }
        assertEquals(listOf(LogLevel.Debug, LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error, LogLevel.Error), levels)
        assertEquals("x", parse("$STAMP [INF] A: x").single().message)
    }

    @Test
    fun `lines without a header are folded into the entry before them`() {
        val entries =
            parse(
                "$STAMP [ERR] [3] Jellyfin.Server: Boom\n" +
                    "System.Exception: bad\n" +
                    "   at Jellyfin.Server.Run()\n" +
                    "$STAMP [INF] [4] Main: After\n",
            )

        assertEquals(2, entries.size)
        assertEquals("Boom\nSystem.Exception: bad\n   at Jellyfin.Server.Run()", entries[0].message)
        assertEquals(LogLevel.Error, entries[0].level)
        assertEquals("After", entries[1].message)
    }

    @Test
    fun `text before the first header, or with no header at all, is kept as an unlabelled entry`() {
        val cutOff = parse("   at the tail of a cut trace\n$STAMP [INF] [1] Main: Started\n")
        assertEquals(2, cutOff.size)
        assertNull(cutOff[0].level)
        assertNull(cutOff[0].timestampRaw)
        assertEquals("   at the tail of a cut trace", cutOff[0].message)

        val prose = parse("not a log line at all")
        assertEquals("not a log line at all", prose.single().message)
        assertNull(prose.single().level)
    }

    @Test
    fun `an empty log has no entries`() {
        assertEquals(emptyList<LogEntry>(), parse(""))
    }

    @Test
    fun `no consumer sees a credential, wherever it sits in an entry`() {
        val entries =
            parse(
                "$STAMP [ERR] [3] A: Request /Videos/1/stream?api_key=abcdef123456 failed\n" +
                    "$STAMP [INF] [1] A: Logging out access token \"$HEX\"\n" +
                    "$STAMP [ERR] [2] B: Failed for session token-1\n" +
                    "   Authorization: Token=\"$HEX\"\n",
                token = "token-1",
            )

        for (entry in entries) {
            for (secret in listOf("abcdef123456", HEX, "token-1")) {
                assertFalse("$secret in message", secret in entry.message)
                assertFalse("$secret in text", secret in entry.text)
            }
        }
        assertTrue("[redacted]" in entries.first().message)
    }

    @Test
    fun `a secret split across two lines is still blanked`() {
        val entries = parse("$STAMP [INF] [1] A: Logging out access token\n\"$HEX\"\n")

        assertFalse(HEX in entries.single().text)
    }
}
