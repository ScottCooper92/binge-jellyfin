package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinNetworkSettingsTest {
    private val base = NetworkSettings(httpPort = 8096, httpsPort = 8920, enableHttps = true)

    private fun impacts(
        app: String,
        after: NetworkSettings,
        before: NetworkSettings = base,
    ) = networkImpacts(app, before, after)

    @Test
    fun `names differ by release, and both read`() {
        val old = JellyfinServerProfile.from("10.8.13")
        val new = JellyfinServerProfile.from("10.9.0")
        assertEquals("HttpServerPortNumber", networkKeys(old).httpPort)
        assertEquals("PublicPort", networkKeys(old).publicHttpPort)
        assertEquals("EnableIPV4", networkKeys(old).ipv4)
        assertEquals("InternalHttpPort", networkKeys(new).httpPort)
        assertEquals("PublicHttpPort", networkKeys(new).publicHttpPort)
        assertEquals("EnableIPv4", networkKeys(new).ipv4)
        val json = Json.parseToJsonElement("""{"HttpServerPortNumber":9000,"EnableIPV6":true,"BaseUrl":"/jf"}""").jsonObject
        val settings = json.networkSettings(networkKeys(old))
        assertEquals(9000, settings.httpPort)
        assertTrue(settings.ipv6)
        assertEquals("/jf", settings.baseUrl)
        assertTrue(settings.has("EnableIPV6"))
    }

    @Test
    fun `a server that leaves an option out has no row for it`() {
        val settings =
            Json
                .parseToJsonElement(
                    """{"InternalHttpPort":8096}""",
                ).jsonObject
                .networkSettings(networkKeys(JellyfinServerProfile.from("10.10.0")))
        assertTrue(settings.has("InternalHttpPort"))
        assertFalse(settings.has("EnableUPnP"))
        assertFalse(settings.has("CertificatePath"))
    }

    @Test
    fun `the base URL is in the network configuration on every release`() {
        val network = Json.parseToJsonElement("""{"InternalHttpPort":8096,"BaseUrl":"/jf"}""").jsonObject
        val settings = network.networkSettings(networkKeys(JellyfinServerProfile.from("10.10.0")))
        assertEquals("/jf", settings.baseUrl)
        assertTrue(settings.has("BaseUrl"))
    }

    @Test
    fun `UPnP is there before 10_11 and not from it`() {
        assertTrue(JellyfinServerProfile.from("10.10.7").hasUpnpOption)
        assertFalse(JellyfinServerProfile.from("10.11.0").hasUpnpOption)
        assertTrue(JellyfinServerProfile.from(null).hasUpnpOption)
        assertFalse(JellyfinServerProfile.from("10.8.13").hasInternalPortNames)
        assertTrue(JellyfinServerProfile.from("10.9.0").hasInternalPortNames)
    }

    @Test
    fun `changing the port this app connects on reaches it, over either scheme`() {
        assertEquals(setOf(NetworkImpact.Port), impacts("http://nas:8096", base.copy(httpPort = 9000)))
        assertEquals(setOf(NetworkImpact.Port), impacts("https://nas:8920", base.copy(httpsPort = 9443)))
        assertEquals(emptySet<NetworkImpact>(), impacts("http://nas:8096", base.copy(httpsPort = 9443)))
        assertEquals(emptySet<NetworkImpact>(), impacts("https://nas:8920", base.copy(httpPort = 9000)))
        // A server reached through a proxy on another port isn't on the port being changed.
        assertEquals(emptySet<NetworkImpact>(), impacts("https://media.example", base.copy(httpPort = 9000)))
    }

    @Test
    fun `turning HTTPS off reaches a client on it, and requiring it reaches one on http`() {
        assertEquals(setOf(NetworkImpact.HttpsOff), impacts("https://nas:8920", base.copy(enableHttps = false)))
        assertEquals(emptySet<NetworkImpact>(), impacts("http://nas:8096", base.copy(enableHttps = false)))
        assertEquals(setOf(NetworkImpact.RequireHttps), impacts("http://nas:8096", base.copy(requireHttps = true)))
        assertEquals(emptySet<NetworkImpact>(), impacts("https://nas:8920", base.copy(requireHttps = true)))
    }

    @Test
    fun `a base URL change and a listen list that leaves the app's address out both reach it`() {
        assertEquals(setOf(NetworkImpact.BaseUrl), impacts("http://nas:8096", base.copy(baseUrl = "/jf")))
        assertEquals(setOf(NetworkImpact.BindAddress), impacts("http://192.168.1.5:8096", base.copy(bindAddresses = listOf("10.0.0.2"))))
        assertEquals(
            emptySet<NetworkImpact>(),
            impacts("http://192.168.1.5:8096", base.copy(bindAddresses = listOf("192.168.1.5", "10.0.0.2"))),
        )
        // Clearing the list listens on everything again.
        assertEquals(
            emptySet<NetworkImpact>(),
            impacts(
                "http://192.168.1.5:8096",
                base.copy(bindAddresses = emptyList()),
                before = base.copy(bindAddresses = listOf("10.0.0.2")),
            ),
        )
    }

    @Test
    fun `a new certificate file with HTTPS on is flagged, and with HTTPS off is not`() {
        val on = base.copy(enableHttps = true, certificatePath = "/a.pfx")
        assertEquals(setOf(NetworkImpact.Certificate), impacts("http://nas:8096", on.copy(certificatePath = "/b.pfx"), before = on))
        val off = base.copy(enableHttps = false, certificatePath = "/a.pfx")
        assertEquals(emptySet<NetworkImpact>(), impacts("http://nas:8096", off.copy(certificatePath = "/b.pfx"), before = off))
        assertTrue(NetworkImpact.Certificate.severe)
    }

    @Test
    fun `narrowing who can connect is flagged, and is not severe`() {
        val after = base.copy(remoteAccess = false, remoteIpFilter = listOf("203.0.113.0/24"), ipv6 = false)
        assertEquals(
            setOf(NetworkImpact.RemoteAccess, NetworkImpact.IpFilter),
            impacts("http://nas:8096", after, before = base.copy(ipv6 = false)),
        )
        assertEquals(setOf(NetworkImpact.Ip), impacts("http://nas:8096", base.copy(ipv4 = false)))
        assertFalse(NetworkImpact.RemoteAccess.severe)
        assertTrue(NetworkImpact.BindAddress.severe)
        assertTrue(NetworkImpact.Port.severe)
    }

    @Test
    fun `no change, and an address that isn't a URL, flag nothing about it`() {
        assertEquals(emptySet<NetworkImpact>(), impacts("http://nas:8096", base))
        assertEquals(setOf(NetworkImpact.RemoteAccess), impacts("not a url", base.copy(remoteAccess = false)))
    }

    @Test
    fun `a base URL is stored with a leading slash and no trailing one`() {
        assertEquals("/jellyfin", normalBaseUrl(" jellyfin/ "))
        assertEquals("/a/b", normalBaseUrl("/a/b/"))
        assertEquals("", normalBaseUrl("  /  "))
        assertEquals("", normalBaseUrl(""))
    }
}
