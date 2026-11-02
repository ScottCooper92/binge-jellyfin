package com.cooper.binge.companion.jellyfin.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogsMappingsTest {
    @Test
    fun `a short name is left as it is`() {
        assertEquals("log_20261031.log", "log_20261031.log".middleEllipsised())
    }

    @Test
    fun `a long name is cut in the middle and keeps its date and extension`() {
        val name = "FFmpeg.Transcode-2026-10-31_09-15-02_3f2a9c.log"
        val cut = name.middleEllipsised()

        assertTrue(cut.length <= 26)
        assertTrue(cut.startsWith("FFmpeg"))
        assertTrue(cut.endsWith("09-15-02_3f2a9c.log"))
        assertTrue("…" in cut)
    }
}
