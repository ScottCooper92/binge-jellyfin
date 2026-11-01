package com.cooper.binge.companion.jellyfin.server

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinLogsApiTest {
    private fun tail(
        text: String,
        max: Int,
    ) = readTail(Buffer().writeUtf8(text), max)

    @Test
    fun `a log under the cap is read whole`() {
        val read = tail("a\nb\nc\n", 100)
        assertEquals("a\nb\nc\n", read.text)
        assertFalse(read.truncated)
    }

    @Test
    fun `a log exactly at the cap is not truncated`() {
        val text = "abcd\nefg\n" // 9 bytes
        val read = tail(text, 9)
        assertEquals(text, read.text)
        assertFalse(read.truncated)
    }

    @Test
    fun `a log over the cap keeps its end and drops the line the cut fell in`() {
        val read = tail("first line\nsecond line\nthird line\n", 20)
        assertTrue(read.truncated)
        assertEquals("third line\n", read.text)
    }

    @Test
    fun `a long log read in many chunks keeps only the last bytes`() {
        val text = (1..5_000).joinToString("") { "line $it\n" }
        val read = tail(text, 1_000)
        assertTrue(read.truncated)
        assertTrue(read.text.endsWith("line 5000\n"))
        assertTrue(read.text.length <= 1_000)
        assertTrue(read.text.startsWith("line "))
    }

    @Test
    fun `credentials in a line are blanked and ordinary text is left alone`() {
        assertEquals("GET /Videos/1/stream?api_key=[redacted] 200", redactSecrets("GET /Videos/1/stream?api_key=abcdef123456 200"))
        assertEquals("""ApiKey: [redacted]""", redactSecrets("ApiKey: 0123456789abcdef"))
        assertEquals("""Authorization Token="[redacted]" ok""", redactSecrets("""Authorization Token="0123456789abcdef" ok"""))
        assertEquals(
            """{"AccessToken":"[redacted]","Name":"x"}""",
            redactSecrets("""{"AccessToken":"0123456789abcdef","Name":"x"}"""),
        )
        assertEquals(
            """[INF] Logging out access token "[redacted]"""",
            redactSecrets("""[INF] Logging out access token "0123456789abcdef0123456789abcdef""""),
        )
        assertEquals("Revoked token [redacted] for x", redactSecrets("Revoked token 0123456789abcdef0123456789abcdef for x"))
        val plain = "[INF] Server started, a token of thanks, key=short"
        assertEquals(plain, redactSecrets(plain))
    }

    @Test
    fun `every credential line Jellyfin's own source logs is blanked`() {
        val hex = "0123456789abcdef0123456789abcdef"
        // Serilog prints a string argument in quotes. Each is read from Jellyfin's source at 10.11.0.
        assertEquals("""Logging out access token "[redacted]"""", redactSecrets("""Logging out access token "$hex""""))
        assertEquals(
            """Authenticated with Schedules Direct token: "[redacted]"""",
            redactSecrets("""Authenticated with Schedules Direct token: "$hex""""),
        )
        assertEquals("""Removing expired secret "[redacted]"""", redactSecrets("""Removing expired secret "$hex""""))
        assertEquals("""Secret "[redacted]" already expired""", redactSecrets("""Secret "$hex" already expired"""))
        assertEquals(
            "GET /Videos/1/stream?ApiKey=[redacted]&static=true responded 200",
            redactSecrets("GET /Videos/1/stream?ApiKey=$hex&static=true responded 200"),
        )
        assertEquals("X-Emby-Token: [redacted]", redactSecrets("X-Emby-Token: $hex"))
    }

    @Test
    fun `a 32-digit hex within a few words after a key word is blanked, and one with no key word is not`() {
        val hex = "0123456789abcdef0123456789abcdef"
        assertEquals("Revoked the token of Bob: [redacted] now", redactSecrets("Revoked the token of Bob: $hex now"))
        assertEquals("The api key was [redacted]", redactSecrets("The api key was $hex"))
        val item = "[INF] Refreshing /Items/$hex/Images, a token of thanks"
        assertEquals(item, redactSecrets(item))
    }
}
