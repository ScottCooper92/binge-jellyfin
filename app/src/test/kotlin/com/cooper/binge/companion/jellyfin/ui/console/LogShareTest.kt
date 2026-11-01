package com.cooper.binge.companion.jellyfin.ui.console

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogShareTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes the text to a log file named for the log`() {
        val file = writeSharedLog(tmp.newFolder("logs"), "log_20261031.log", "hello")
        assertEquals("log_20261031.log", file.name)
        assertEquals("hello", file.readText())
    }

    @Test
    fun `a name that isn't a safe file name is sanitised, and given a log extension`() {
        val dir = tmp.newFolder("logs")
        assertEquals("a_b_c.log", writeSharedLog(dir, "a/b\\c", "x").name)
        assertEquals("log.log", writeSharedLog(dir, "  ", "x").name)
        assertTrue(writeSharedLog(dir, "../../etc", "x").parentFile == dir)
    }

    @Test
    fun `the last file shared is replaced`() {
        val dir = tmp.newFolder("logs")
        val old = writeSharedLog(dir, "one.log", "1")
        val new = writeSharedLog(dir, "two.log", "2")
        assertFalse(old.exists())
        assertEquals(listOf("two.log"), dir.list()!!.toList())
        assertTrue(new.exists())
    }
}
