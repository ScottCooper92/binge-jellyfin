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
        val plain = "[INF] Server started, a token of thanks, key=short"
        assertEquals(plain, redactSecrets(plain))
    }
}
