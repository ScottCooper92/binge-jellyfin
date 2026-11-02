package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinLiveTvApiTest {
    private fun configuration(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun `a web address is shown as its host, never with the account a playlist carries in it`() {
        assertEquals(
            "https://iptv.example:8443",
            "https://bob:hunter2@iptv.example:8443/get.php?username=bob&password=hunter2".withoutSecrets(),
        )
        assertEquals("http://192.168.1.50", "http://192.168.1.50".withoutSecrets())
        assertEquals("http://tuner.lan:8080", "http://tuner.lan:8080/lineup.json".withoutSecrets())
        assertEquals("/config/guide.xml", "/config/guide.xml".withoutSecrets())
        assertEquals("", "".withoutSecrets())
    }

    @Test
    fun `tuners and providers are read without a password, and a tuner with no name is named by its address`() {
        val summary =
            configuration(
                """{"TunerHosts":[{"Id":"t1","Type":"m3u","FriendlyName":"IPTV","Url":"https://iptv.example/get.php?password=x"},""" +
                    """{"Id":"t2","Type":"hdhomerun","FriendlyName":" ","Url":"http://192.168.1.50"},{"Type":"m3u"}],""" +
                    """"ListingProviders":[{"Id":"g1","Type":"xmltv","Path":"https://guide.example/x.xml?key=abc"},""" +
                    """{"Id":"g2","Type":"SchedulesDirect","Username":"alice","Password":"5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8"}]}""",
            ).liveTvSummary()

        assertEquals(listOf("IPTV", "http://192.168.1.50"), summary.tuners.map { it.name })
        assertEquals(listOf("m3u", "hdhomerun"), summary.tuners.map { it.type })
        assertEquals("https://iptv.example", summary.tuners[0].address)
        assertEquals(listOf("https://guide.example", "alice"), summary.providers.map { it.label })
        val everything = summary.toString()
        assertFalse(everything.contains("5baa61e4"))
        assertFalse(everything.contains("password"))
        assertFalse(everything.contains("abc"))
    }

    @Test
    fun `a configuration with neither tuners nor providers is an empty summary`() {
        val summary = configuration("""{"GuideDays":7}""").liveTvSummary()
        assertTrue(summary.tuners.isEmpty())
        assertTrue(summary.providers.isEmpty())
        assertNull(summary.guideEndMillis)
    }
}
