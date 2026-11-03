package com.cooper.binge.companion.jellyfin.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinSchedulesDirectApiTest {
    @Test
    fun `the password is hashed as SHA-1 in lowercase hex, which is what Schedules Direct takes`() {
        assertEquals("5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8", sha1Hex("password"))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", sha1Hex(""))
    }

    @Test
    fun `a new account carries the hash and never the password`() {
        val account = newSchedulesDirectProvider("alice", "password")
        assertEquals("SchedulesDirect", account.getValue("Type").jsonPrimitive.content)
        assertEquals("alice", account.getValue("Username").jsonPrimitive.content)
        assertEquals("5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8", account.getValue("Password").jsonPrimitive.content)
        assertFalse(account.toString().contains("\"password\""))
    }

    @Test
    fun `the countries are read from Schedules Direct's regions, by name`() {
        val countries =
            Json
                .parseToJsonElement(
                    """{"Europe":[{"fullName":"United Kingdom","shortName":"GBR"}],"North America":[{"fullName":"United States","shortName":"USA"},{"nope":1}]}""",
                ).jsonObject
                .sdCountries()
        assertEquals(listOf("United Kingdom", "United States"), countries.map { it.name })
        assertEquals(listOf("GBR", "USA"), countries.map { it.code })
        assertEquals("Europe", countries.first().region)
    }

    @Test
    fun `a shape this app does not know reads as no countries`() {
        assertTrue(
            Json
                .parseToJsonElement("""{"Europe":"unexpected"}""")
                .jsonObject
                .sdCountries()
                .isEmpty(),
        )
    }

    @Test
    fun `a lineup is added to the provider as the server holds it, with its country and postal code`() {
        val held =
            Json
                .parseToJsonElement(
                    """{"Id":"g2","Type":"SchedulesDirect","Username":"alice","Password":"abc","Future":1}""",
                ).jsonObject
        val saved = held.withLineup("USA-NY12345-X", "USA", "10001")
        assertEquals("USA-NY12345-X", saved.getValue("ListingsId").jsonPrimitive.content)
        assertEquals("USA", saved.getValue("Country").jsonPrimitive.content)
        assertEquals("10001", saved.getValue("ZipCode").jsonPrimitive.content)
        assertEquals("abc", saved.getValue("Password").jsonPrimitive.content)
        assertEquals("1", saved.getValue("Future").jsonPrimitive.content)
    }

    @Test
    fun `an account with no lineup is listed as not finished, and one with a lineup is not`() {
        val config =
            Json
                .parseToJsonElement(
                    """{"ListingProviders":[{"Id":"a","Type":"SchedulesDirect","Username":"alice"},""" +
                        """{"Id":"b","Type":"SchedulesDirect","Username":"bob","ListingsId":"USA-X"},{"Id":"c","Type":"xmltv","Path":"/g.xml"}]}""",
                ).jsonObject
        assertEquals(listOf(true, false, false), config.liveTvSummary().providers.map { it.lineupPending })
    }
}
