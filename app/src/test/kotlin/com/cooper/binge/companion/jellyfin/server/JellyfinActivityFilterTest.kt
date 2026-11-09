package com.cooper.binge.companion.jellyfin.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class JellyfinActivityFilterTest {
    @Test
    fun `no filter sends nothing and is empty`() {
        assertTrue(ActivityFilter().isEmpty)
        assertEquals(emptyMap<String, String>(), ActivityFilter().query())
        assertEquals(0, ActivityFilter().count)
    }

    @Test
    fun `each filter is the server's own parameter, a time as an ISO instant and a severity by its name`() {
        val filter =
            ActivityFilter(
                fromMillis = Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(),
                toMillis = Instant.parse("2026-11-08T23:59:59.999Z").toEpochMilli(),
                who = ActivityWho.Users,
                severity = Severity.Warning,
                type = "VideoPlayback",
            )
        assertEquals(
            mapOf(
                "minDate" to "2026-11-01T00:00:00Z",
                "maxDate" to "2026-11-08T23:59:59.999Z",
                "hasUserId" to "true",
                "severity" to "Warning",
                "type" to "VideoPlayback",
            ),
            filter.query(),
        )
        assertEquals(4, filter.count)
        assertFalse(filter.isEmpty)
    }

    @Test
    fun `system activity is entries with no user, and a named user leaves the split out`() {
        assertEquals(mapOf("hasUserId" to "false"), ActivityFilter(who = ActivityWho.System).query())
        assertEquals(mapOf("username" to "sam"), ActivityFilter(who = ActivityWho.Users, username = "sam").query())
    }

    @Test
    fun `a blank user or type is not sent`() {
        assertEquals(emptyMap<String, String>(), ActivityFilter(username = " ", type = "").query())
    }

    @Test
    fun `a preset reaches back from now, and Today starts at the start of the day in the zone`() {
        val now = Instant.parse("2026-11-09T15:30:00Z")
        assertEquals(Instant.parse("2026-11-02T15:30:00Z"), ActivityTimePreset.Last7Days.from(now, ZoneOffset.UTC))
        assertEquals(Instant.parse("2026-10-10T15:30:00Z"), ActivityTimePreset.Last30Days.from(now, ZoneOffset.UTC))
        assertEquals(Instant.parse("2026-11-09T00:00:00Z"), ActivityTimePreset.Today.from(now, ZoneOffset.UTC))
        assertEquals(Instant.parse("2026-11-08T22:00:00Z"), ActivityTimePreset.Today.from(now, ZoneOffset.ofHours(2)))
    }
}
