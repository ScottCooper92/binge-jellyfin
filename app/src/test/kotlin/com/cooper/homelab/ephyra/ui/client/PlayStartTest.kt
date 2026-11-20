package com.cooper.homelab.ephyra.ui.client

import com.cooper.homelab.ephyra.demo.DEMO_ORIGIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What stops a play on the saved server: only the demo, and plain http at a public address. */
class PlayStartTest {
    @Test
    fun `the demo has nothing to play`() {
        assertEquals(PlayBlock.Demo, playBlockFor(DEMO_ORIGIN))
        assertEquals(PlayBlock.Demo, playBlockFor("$DEMO_ORIGIN/"))
    }

    @Test
    fun `plain http at a public address is refused`() {
        assertEquals(PlayBlock.PublicHttp, playBlockFor("http://jellyfin.example.com"))
        assertEquals(PlayBlock.PublicHttp, playBlockFor("http://203.0.113.9:8096"))
    }

    @Test
    fun `https anywhere, and http on the home network, play`() {
        assertNull(playBlockFor("https://jellyfin.example.com"))
        assertNull(playBlockFor("http://192.168.1.10:8096"))
        assertNull(playBlockFor("http://10.0.0.5:8096"))
        assertNull(playBlockFor("http://localhost:8096"))
    }
}
