package com.cooper.binge.companion.jellyfin.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicenceEntryTest {
    private fun raw(
        name: String,
        version: String? = "1.0",
        licences: List<String> = listOf("Apache-2.0"),
        website: String? = "https://example.com",
        id: String = name,
    ) = RawLibrary(id, name, version, licences, website)

    @Test
    fun `libraries are listed by name ignoring case`() {
        val entries = listOf(raw("zebra"), raw("Apple"), raw("mango")).toLicenceEntries()
        assertEquals(listOf("Apple", "mango", "zebra"), entries.map { it.name })
    }

    @Test
    fun `blank fields read as absent and a blank name falls back to the id`() {
        val entry =
            listOf(raw(name = " ", id = "com.example:lib", version = "", licences = listOf("", "MIT", "MIT"), website = " "))
                .toLicenceEntries()
                .single()
        assertEquals("com.example:lib", entry.name)
        assertNull(entry.version)
        assertEquals(listOf("MIT"), entry.licences)
        assertNull(entry.website)
    }

    @Test
    fun `no libraries is an empty list`() {
        assertEquals(emptyList<LicenceEntry>(), emptyList<RawLibrary>().toLicenceEntries())
    }
}
