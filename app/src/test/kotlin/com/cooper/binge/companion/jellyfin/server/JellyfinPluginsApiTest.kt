package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinPluginsApiTest {
    private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun `a plugin status is read by name, the server's spelling and the corrected one alike`() {
        assertEquals(PluginStatus.Active, pluginStatus("Active"))
        assertEquals(PluginStatus.Restart, pluginStatus("Restart"))
        assertEquals(PluginStatus.Superseded, pluginStatus("Superceded"))
        assertEquals(PluginStatus.Superseded, pluginStatus("Superseded"))
        assertEquals(PluginStatus.Unknown, pluginStatus("FromTheFuture"))
        assertEquals(PluginStatus.Unknown, pluginStatus(null))
        assertEquals(PluginStatus.Unknown, pluginStatus("Unknown"))
    }

    @Test
    fun `only a plugin that can run or has been turned off can be toggled`() {
        val toggle = PluginStatus.entries.filter { it.canToggle }.toSet()
        assertEquals(setOf(PluginStatus.Active, PluginStatus.Disabled, PluginStatus.Restart), toggle)
    }

    @Test
    fun `a package version fits a server of its target release or newer, and one with no target fits any`() {
        fun version(abi: String?) = PackageVersion("1.0.0.0", null, abi, "Repo", "https://r.example/m.json")
        val server = JellyfinVersion.parse("10.10.7")
        assertTrue(version("10.10.0.0").fits(server))
        assertTrue(version("10.9.0.0").fits(server))
        assertTrue(!version("10.11.0.0").fits(server))
        assertTrue(version(null).fits(server))
        assertTrue(version("").fits(server))
        assertTrue(version("10.11.0.0").fits(null))
        assertTrue(version("not a version").fits(server))
    }

    @Test
    fun `a package offers the newest version built for this server, or none`() {
        val pkg =
            CatalogPackage(
                "g",
                "Playback Reporting",
                "",
                "",
                "jellyfin",
                "General",
                listOf("14.0.0.0" to "10.11.0.0", "12.0.0.0" to "10.9.0.0", "13.0.0.0" to "10.10.0.0").map { (v, abi) ->
                    PackageVersion(v, null, abi, "Repo", "https://r.example/m.json")
                },
            )
        assertEquals("13.0.0.0", pkg.installable(JellyfinVersion.parse("10.10.7"))?.version)
        assertEquals("12.0.0.0", pkg.installable(JellyfinVersion.parse("10.9.0"))?.version)
        assertNull(pkg.installable(JellyfinVersion.parse("10.8.13")))
        assertEquals("14.0.0.0", pkg.installable(null)?.version)
    }

    @Test
    fun `an install the server announces is read, and any other message is not`() {
        assertEquals(
            InstallEvent.Started("0123", "Playback Reporting"),
            json(
                """{"MessageType":"PackageInstalling","Data":{"Guid":"0123","Name":"Playback Reporting","Version":"13.0.0.0"}}""",
            ).installEvent(),
        )
        assertEquals(InstallEvent.Done("A"), json("""{"MessageType":"PackageInstallationCompleted","Data":{"Name":"A"}}""").installEvent())
        assertEquals(InstallEvent.Failed("A"), json("""{"MessageType":"PackageInstallationFailed","Data":{"Name":"A"}}""").installEvent())
        assertEquals(
            InstallEvent.Cancelled("A"),
            json("""{"MessageType":"PackageInstallationCancelled","Data":{"Name":"A"}}""").installEvent(),
        )
        assertNull(json("""{"MessageType":"Sessions","Data":[]}""").installEvent())
        assertNull(json("""{"MessageType":"PackageInstalling","Data":{"Name":"A"}}""").installEvent())
        assertNull(json("""{"MessageType":"PackageInstallationCompleted"}""").installEvent())
    }

    @Test
    fun `a repository is sent back as the server sent it, and a new one has only what it needs`() {
        val read =
            PluginRepository(
                "Stable",
                "https://r.example/m.json",
                true,
                json("""{"Name":"Stable","Url":"https://r.example/m.json","Enabled":true,"Future":1}"""),
            )
        assertEquals("""{"Name":"Stable","Url":"https://r.example/m.json","Enabled":true,"Future":1}""", read.toJson().toString())
        assertEquals(
            """{"Name":"Mine","Url":"https://m.example/m.json","Enabled":true}""",
            PluginRepository("Mine", "https://m.example/m.json", enabled = true).toJson().toString(),
        )
    }
}
