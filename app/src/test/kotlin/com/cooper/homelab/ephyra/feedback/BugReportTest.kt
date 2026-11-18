package com.cooper.homelab.ephyra.feedback

import com.cooper.homelab.core.feedback.bugReportUrl
import com.cooper.homelab.ephyra.server.JellyfinVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    private val report =
        ephyraBugReport(
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
            bugReportUrl(BUG_REPORT_REPOSITORY, report),
        )
    }

    @Test
    fun `a server whose release is unknown leaves the server field out`() {
        val url = bugReportUrl(BUG_REPORT_REPOSITORY, ephyraBugReport("0.1.0 (101)", "Google Pixel 10", "17 (API 37)"))
        assertFalse(url.contains("server="))
        assertTrue(url.contains("android-version="))
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
