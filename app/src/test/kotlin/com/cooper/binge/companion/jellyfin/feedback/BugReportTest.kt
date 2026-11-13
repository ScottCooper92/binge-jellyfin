package com.cooper.binge.companion.jellyfin.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    private val report = DeviceReport(appVersion = "0.1.0 (101)", device = "Google Pixel 10, Android 17 (API 37)")

    @Test
    fun `the link opens the bug form with each field filled by its own id`() {
        assertEquals(
            "https://github.com/ScottCooper92/binge-jellyfin/issues/new" +
                "?template=bug.yml&app-version=0.1.0+%28101%29&device=Google+Pixel+10%2C+Android+17+%28API+37%29",
            bugReportUrl(report),
        )
    }

    @Test
    fun `a repository with a trailing slash makes the same link, and a fork's own repository is used`() {
        assertEquals(bugReportUrl(report), bugReportUrl(report, BUG_REPORT_REPOSITORY + "/"))
        assertTrue(bugReportUrl(report, "https://github.com/someone/fork").startsWith("https://github.com/someone/fork/issues/new?"))
    }

    @Test
    fun `nothing about the server is in the link`() {
        val url = bugReportUrl(report)
        assertFalse(url.contains("server-version"))
        assertFalse(url.contains("body="))
    }

    @Test
    fun `the install's link carries its own version and device`() {
        val url = installBugReportUrl()
        assertTrue(url.contains("app-version="))
        assertTrue(url.contains("device="))
    }
}
