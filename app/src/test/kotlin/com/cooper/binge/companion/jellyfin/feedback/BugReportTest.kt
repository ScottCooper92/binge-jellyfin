package com.cooper.binge.companion.jellyfin.feedback

import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    private val report =
        DeviceReport(
            appVersion = "0.1.0 (101)",
            device = "Google Pixel 10",
            androidVersion = "17 (API 37)",
            server = "Jellyfin 10.10.7",
        )

    @Test
    fun `the link opens the Ephyra bug form on app-feedback with each field filled by its own id`() {
        assertEquals(
            "https://github.com/ScottCooper92/app-feedback/issues/new" +
                "?template=ephyra-bug.yml&app-version=0.1.0+%28101%29&device=Google+Pixel+10" +
                "&android-version=17+%28API+37%29&server=Jellyfin+10.10.7",
            bugReportUrl(report),
        )
    }

    @Test
    fun `a server whose release is unknown leaves the server field out`() {
        val url = bugReportUrl(report.copy(server = null))
        assertFalse(url.contains("server="))
        assertTrue(url.contains("android-version="))
    }

    @Test
    fun `a repository with a trailing slash makes the same link, and a fork's own repository is used`() {
        assertEquals(bugReportUrl(report), bugReportUrl(report, BUG_REPORT_REPOSITORY + "/"))
        assertTrue(bugReportUrl(report, "https://github.com/someone/fork").startsWith("https://github.com/someone/fork/issues/new?"))
    }

    @Test
    fun `the link never carries a body or the server's address`() {
        assertFalse(bugReportUrl(report).contains("body="))
    }

    @Test
    fun `the install's link carries its own version, device and Android version`() {
        val url = installBugReportUrl()
        assertTrue(url.contains("app-version="))
        assertTrue(url.contains("device="))
        assertTrue(url.contains("android-version="))
        assertFalse(url.contains("server="))
    }

    @Test
    fun `the install's link names the connected server's release`() {
        assertTrue(installBugReportUrl(JellyfinVersion(10, 10, 7)).endsWith("&server=Jellyfin+10.10.7"))
    }
}
