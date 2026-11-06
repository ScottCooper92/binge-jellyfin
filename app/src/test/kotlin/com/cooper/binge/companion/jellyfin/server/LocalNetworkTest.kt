package com.cooper.binge.companion.jellyfin.server

import com.cooper.binge.companion.jellyfin.ui.ConnectionHealth
import com.cooper.binge.companion.jellyfin.ui.unreachableHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which servers need the local-network permission, and what a failed connection to each says about it. */
class LocalNetworkTest {
    private val denied = LocalNetworkPermission { false }
    private val granted = LocalNetworkPermission { true }

    @Test
    fun `private, link-local and unique-local addresses, home names and single-label names need the permission`() {
        listOf(
            "10.0.0.5",
            "172.16.4.1",
            "172.31.255.255",
            "192.168.1.20",
            "169.254.3.4",
            "fd12:3456::1",
            "nas.local",
            "nas.lan",
            "router.home.arpa",
            "box.internal",
            "nas",
        ).forEach { assertTrue("$it should need it", it.isLocalNetworkHost()) }
    }

    @Test
    fun `public hosts, loopback and tailnet addresses do not`() {
        listOf(
            "203.0.113.9",
            "172.32.0.1",
            "jellyfin.example.com",
            "localhost",
            "127.0.0.1",
            "100.101.102.103",
            "fd7a:115c:a1e0::1",
            "nas.tail1234.ts.net",
        ).forEach { assertFalse("$it should not need it", it.isLocalNetworkHost()) }
    }

    @Test
    fun `a server address says its host only when reaching it needs the permission`() {
        assertEquals("192.168.1.2", "http://192.168.1.2:8096".localNetworkHostOrNull())
        assertEquals("nas.local", "http://nas.local:8096".localNetworkHostOrNull())
        assertNull("https://jellyfin.example.com".localNetworkHostOrNull())
        assertNull("not a url".localNetworkHostOrNull())
        assertNotNull("http://[fd12:3456::1]:8096".localNetworkHostOrNull())
    }

    @Test
    fun `a local server that did not answer, without the permission, is the permission's fault`() {
        assertEquals(ConnectionHealth.LocalNetworkDenied, unreachableHealth("http://192.168.1.2:8096", denied))
        assertEquals(ConnectionHealth.LocalNetworkDenied, unreachableHealth("http://nas.local:8096", denied))
    }

    @Test
    fun `with the permission, or for a server it does not cover, a silent server is just unreachable`() {
        assertEquals(ConnectionHealth.Unreachable, unreachableHealth("http://192.168.1.2:8096", granted))
        assertEquals(ConnectionHealth.Unreachable, unreachableHealth("https://jellyfin.example.com", denied))
        assertEquals(ConnectionHealth.Unreachable, unreachableHealth("http://100.101.102.103:8096", denied))
    }
}
