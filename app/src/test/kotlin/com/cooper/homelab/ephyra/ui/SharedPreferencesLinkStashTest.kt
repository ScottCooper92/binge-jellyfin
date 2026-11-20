package com.cooper.homelab.ephyra.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.cooper.homelab.ephyra.LINK_STASH_FILE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesLinkStashTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val preferences = context.getSharedPreferences("link-stash-test", Context.MODE_PRIVATE)

    /** The preferences, counting how each edit was ended: `commit` returns once the file is written, `apply` does not wait for it. */
    private class CountingPreferences(
        private val real: SharedPreferences,
    ) : SharedPreferences by real {
        var commits = 0
        var applies = 0

        override fun edit(): SharedPreferences.Editor {
            val editor = real.edit()
            return object : SharedPreferences.Editor by editor {
                override fun commit(): Boolean {
                    commits++
                    return editor.commit()
                }

                override fun apply() {
                    applies++
                    editor.apply()
                }
            }
        }
    }

    @Test
    fun `a record is read back as written, replaced by the next, and gone once cleared`() {
        val stash = SharedPreferencesLinkStash(preferences)
        assertNull(stash.read())

        stash.write("first")
        assertEquals("first", stash.read())
        stash.write("second")
        assertEquals("second", stash.read())
        stash.clear()

        assertNull(stash.read())
    }

    @Test
    fun `every write and clear is committed, never applied, so a kill right after cannot lose it`() {
        val counting = CountingPreferences(preferences)
        val stash = SharedPreferencesLinkStash(counting)

        stash.write("record")
        stash.clear()

        assertEquals(2, counting.commits)
        assertEquals("nothing is left to be written later", 0, counting.applies)
    }

    @Test
    fun `the record is in the file as soon as write returns, and out of it as soon as clear does`() {
        val stash = SharedPreferencesLinkStash(preferences)
        val file = File(context.applicationInfo.dataDir, "shared_prefs/link-stash-test.xml")

        stash.write("sealed-record")
        assertTrue("on disk when write returns", file.readText().contains("sealed-record"))
        stash.clear()

        assertTrue("off disk when clear returns", !file.readText().contains("sealed-record"))
    }

    @Test
    fun `the file is private to the app and named for what it holds`() {
        val file = context.getSharedPreferences(LINK_STASH_FILE, Context.MODE_PRIVATE)
        SharedPreferencesLinkStash(file).write("record")

        assertEquals("quick_connect_pending", LINK_STASH_FILE)
        assertTrue(File(context.applicationInfo.dataDir, "shared_prefs/$LINK_STASH_FILE.xml").exists())
    }
}
