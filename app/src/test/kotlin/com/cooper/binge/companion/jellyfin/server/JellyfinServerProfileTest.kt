package com.cooper.binge.companion.jellyfin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinServerProfileTest {
    @Test
    fun `versions parse from what servers report`() {
        assertEquals(JellyfinVersion(10, 11, 2), JellyfinVersion.parse("10.11.2"))
        assertEquals(JellyfinVersion(10, 10, 0), JellyfinVersion.parse("10.10"))
        assertEquals(JellyfinVersion(10, 9, 11), JellyfinVersion.parse("10.9.11-unstable"))
        assertNull(JellyfinVersion.parse("unknown"))
        assertNull(JellyfinVersion.parse(""))
        assertNull(JellyfinVersion.parse(null))
    }

    @Test
    fun `versions order by their numbers, not their text`() {
        assertTrue(JellyfinVersion(10, 10, 0) > JellyfinVersion(10, 9, 11))
        assertTrue(JellyfinVersion(10, 11, 0) > JellyfinVersion(10, 10, 7))
    }

    @Test
    fun `each feature turns on at the release that added it`() {
        val v108 = JellyfinServerProfile.from("10.8.13")
        assertFalse(v108.hasTrickplay)
        assertFalse(v108.hasLyrics)
        assertFalse(v108.canDisableLibraries)
        val v109 = JellyfinServerProfile.from("10.9.0")
        assertTrue(v109.hasTrickplay)
        assertTrue(v109.hasContentManagementPermissions)
        assertFalse(v109.hasMediaSegments)
        val v1010 = JellyfinServerProfile.from("10.10.7")
        assertTrue(v1010.hasMediaSegments)
        assertFalse(v1010.hasBackups)
        val v1011 = JellyfinServerProfile.from("10.11.0")
        assertTrue(v1011.hasBackups)
        assertTrue(v1011.hasParentalSubRating)
        assertTrue(v1011.hasSeparateCollectionGrouping)
    }

    @Test
    fun `a version that can't be read hides nothing`() {
        val unknown = JellyfinServerProfile.from("nightly")
        assertNull(unknown.version)
        assertTrue(unknown.hasBackups)
        assertTrue(unknown.hasTrickplay)
    }
}
