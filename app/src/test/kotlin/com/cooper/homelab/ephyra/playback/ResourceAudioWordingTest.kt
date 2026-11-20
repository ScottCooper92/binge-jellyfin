package com.cooper.homelab.ephyra.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The second line of an audio row: the format and the layout, as the player words its own rows. */
@RunWith(RobolectricTestRunner::class)
class ResourceAudioWordingTest {
    private val wording = ResourceAudioWording(ApplicationProvider.getApplicationContext<Context>())

    private fun say(
        codec: String?,
        channels: Int?,
        profile: String? = null,
    ) = wording.describe(AudioFormat(codec, profile, channels))

    @Test
    fun `a format and a layout read the way the player's rows do`() {
        assertEquals("AAC mono", say("aac", 1))
        assertEquals("AAC stereo", say("aac", 2))
        assertEquals("Dolby Digital 5.1", say("ac3", 6))
        assertEquals("Dolby Digital Plus 7.1", say("eac3", 8))
        assertEquals("DTS-HD 5.1", say("dts", 6, profile = "DTS-HD MA"))
        assertEquals("DTS 5.1", say("dts", 6, profile = "DTS"))
    }

    @Test
    fun `atmos is named from the profile, on either carrier`() {
        assertEquals("Dolby Atmos 5.1", say("eac3", 6, profile = "Dolby Digital Plus + Dolby Atmos"))
        assertEquals("Dolby Atmos 7.1", say("truehd", 8, profile = "Dolby TrueHD + Dolby Atmos"))
        assertEquals("Dolby TrueHD 7.1", say("truehd", 8))
    }

    @Test
    fun `where the stream gives only one of them the line is that one, and nothing is nothing`() {
        assertEquals("Opus", say("opus", null))
        assertEquals("stereo", say("somethingnew", 2))
        assertEquals("3 channels", say(null, 3))
        assertNull(say(null, null))
    }

    @Test
    @Config(qualifiers = "es")
    fun `the layout is said in the user's language`() {
        assertEquals("AAC estéreo", say("aac", 2))
        assertEquals("AAC 3 canales", say("aac", 3))
    }
}
