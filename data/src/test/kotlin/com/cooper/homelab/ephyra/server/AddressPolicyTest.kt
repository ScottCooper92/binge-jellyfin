package com.cooper.homelab.ephyra.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressPolicyTest {
    private fun AddressPolicy.urls(raw: String) = candidates(raw).map { it.toString() }

    @Test
    fun `the released policy is one address with http added when no scheme was typed`() {
        assertEquals(listOf("http://192.168.1.10:8096/"), AddressPolicy.Legacy.urls(" 192.168.1.10:8096/ "))
        assertEquals(listOf("http://nas/"), AddressPolicy.Legacy.urls("nas"))
        assertTrue(AddressPolicy.Legacy.urls("").isEmpty())
    }

    @Test
    fun `the shared policy tries a local host on http and then on Jellyfin's http port`() {
        assertEquals(listOf("http://192.168.1.10/", "http://192.168.1.10:8096/"), AddressPolicy.Shared.urls("192.168.1.10"))
    }

    @Test
    fun `the shared policy tries a public host on https and then on Jellyfin's https port`() {
        assertEquals(
            listOf("https://jellyfin.example.com/", "https://jellyfin.example.com:8920/"),
            AddressPolicy.Shared.urls("jellyfin.example.com"),
        )
    }

    @Test
    fun `a typed scheme and port are taken as meant and tried alone`() {
        assertEquals(listOf("http://nas.lan:9000/"), AddressPolicy.Shared.urls("http://nas.lan:9000"))
        assertEquals(listOf("https://jf.example.com:8443/"), AddressPolicy.Shared.urls("https://jf.example.com:8443"))
    }

    @Test
    fun `the shared policy keeps a base path`() {
        assertEquals(
            listOf("https://example.com/jellyfin/", "https://example.com:8920/jellyfin/"),
            AddressPolicy.Shared.urls("https://example.com/jellyfin"),
        )
    }

    @Test
    fun `the shared policy refuses what is not an address`() {
        assertTrue(AddressPolicy.Shared.urls("").isEmpty())
        assertTrue(AddressPolicy.Shared.urls("ftp://nas").isEmpty())
        assertTrue(AddressPolicy.Shared.urls("not an address").isEmpty())
    }
}
