package com.cooper.homelab.ephyra

import com.cooper.homelab.ephyra.auth.JellyfinSession
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a session hands the shared redaction follower: this app's own say on what is replaced anywhere and what only as a whole word. */
class SessionRedactionTest {
    private val session =
        JellyfinSession(
            baseUrl = "http://jf.example:8096/",
            serverName = "truenas",
            serverId = "server-1",
            serverVersion = "10.11.6",
            userId = "user-1",
            userName = "alice-the-watcher",
            token = "token-1",
        )

    @Test
    fun `the address, the token and the user's name are replaced wherever they appear`() {
        assertEquals(listOf("http://jf.example:8096", "token-1", "alice-the-watcher"), session.toRedactionLiterals().anywhere)
    }

    @Test
    fun `the host with its port is replaced as a whole word only`() {
        assertEquals(listOf("jf.example:8096"), session.toRedactionLiterals().wholeToken)
        assertEquals(listOf("jellyfin"), session.copy(baseUrl = "http://jellyfin").toRedactionLiterals().wholeToken)
    }

    @Test
    fun `a path after the host is not part of it, and a blank name is dropped`() {
        val literals = session.copy(baseUrl = "https://jf.example/jellyfin/", userName = " ").toRedactionLiterals()
        assertEquals(listOf("https://jf.example/jellyfin", "token-1"), literals.anywhere)
        assertEquals(listOf("jf.example"), literals.wholeToken)
    }
}
