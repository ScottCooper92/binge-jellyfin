package com.cooper.homelab.ephyra.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** What the demo draws for a picture: its shape and size, the ones it leaves out on purpose, and colours that read. */
class DemoPicturesTest {
    private fun url(
        id: String,
        kind: String = "Primary",
        width: Int? = 300,
        host: String = DEMO_HOST,
    ) = "https://$host/Items/$id/Images/$kind?tag=p-$id" + (width?.let { "&maxWidth=$it" } ?: "")

    @Test
    fun `a film's poster is two thirds as wide as it is tall, with its initials and its title`() {
        val poster = demoPictureFor(url("demo-movie-1"))!!
        assertEquals(DemoPictureShape.Poster, poster.shape)
        assertEquals(300 to 450, poster.width to poster.height)
        assertEquals("LQ", poster.label)
        assertEquals("The Long Quiet", poster.caption)
    }

    @Test
    fun `a backdrop and a thumb are wide`() {
        listOf("Backdrop", "Thumb").forEach { kind ->
            val wide = demoPictureFor(url("demo-movie-1", kind, width = 640))!!
            assertEquals(DemoPictureShape.Wide, wide.shape)
            assertEquals(640 to 360, wide.width to wide.height)
        }
    }

    @Test
    fun `an episode's still is sixteen by nine and carries its number`() {
        val still = demoPictureFor(url("demo-ep-4-s1-5", width = 320))!!
        assertEquals(DemoPictureShape.Still, still.shape)
        assertEquals(320 to 180, still.width to still.height)
        assertEquals("E5", still.label)
        // An episode with no number falls back to its title's initials.
        assertEquals("O", demoPictureFor(url("demo-ep-4-s0-x"))!!.label)
    }

    @Test
    fun `a season is labelled by its number and the specials say so`() {
        assertEquals("S1", demoPictureFor(url("demo-series-4-s1"))!!.label)
        assertEquals("SP", demoPictureFor(url("demo-series-4-s0"))!!.label)
    }

    @Test
    fun `a person with a photo is a square of initials with no caption`() {
        val person = demoPictureFor(url("demo-person-1", width = 96))!!
        assertEquals(DemoPictureShape.Person, person.shape)
        assertEquals(96 to 96, person.width to person.height)
        assertEquals("SR", person.label)
        assertNull(person.caption)
    }

    @Test
    fun `the pictures the demo leaves out on purpose are not drawn`() {
        // Robin Hale has no photo, and a hand-made box set has a poster and no backdrop or thumb.
        assertNull(demoPictureFor(url(ROBIN_HALE)))
        assertNotNull(demoPictureFor(url("demo-box-1")))
        assertNull(demoPictureFor(url("demo-box-1", "Backdrop")))
        assertNull(demoPictureFor(url("demo-box-1", "Thumb")))
        // Only the Movies library has a thumb and a backdrop.
        assertNotNull(demoPictureFor(url(DemoLibraries.SEEDED.getValue("Movies"), "Thumb")))
        assertNull(demoPictureFor(url(DemoLibraries.SEEDED.getValue("TV shows"), "Thumb")))
        assertNotNull(demoPictureFor(url(DemoLibraries.SEEDED.getValue("TV shows"))))
    }

    @Test
    fun `an address that is not the demo's, or names nothing the demo has, is not drawn`() {
        assertNull(demoPictureFor(url("demo-movie-1", host = "jellyfin.example.com")))
        assertNull(demoPictureFor("https://$DEMO_HOST/Items/nobody/Images/Primary"))
        assertNull(demoPictureFor("https://$DEMO_HOST/Items/demo-movie-1/Images"))
        assertNull(demoPictureFor("https://$DEMO_HOST/Users/demo-user/Images/Primary"))
        assertNull(demoPictureFor("not an address"))
        assertTrue(url("demo-movie-1").isDemoUrl())
        assertFalse(url("demo-movie-1", host = "jellyfin.example.com").isDemoUrl())
    }

    @Test
    fun `the width is what the address asks for, kept to a sensible range`() {
        assertEquals(300, demoPictureFor(url("demo-movie-1", width = null))!!.width)
        assertEquals(16, demoPictureFor(url("demo-movie-1", width = 3))!!.width)
        assertEquals(1_920, demoPictureFor(url("demo-movie-1", width = 99_999))!!.width)
    }

    @Test
    fun `a title looks the same every time and its neighbours look different`() {
        val ids = (1..40).map { "demo-film-$it" }
        val first = ids.map { demoPictureFor(url(it))!!.topColor }
        assertEquals(first, ids.map { demoPictureFor(url(it))!!.topColor })
        assertTrue("only ${first.toSet().size} colours in 40 films", first.toSet().size >= 20)
    }

    @Test
    fun `white text reads on every colour the gradient can start or end with`() {
        (0 until 360).forEach { hue ->
            val top = hsv(hue.toFloat(), 0.55f, 0.44f)
            val bottom = hsv(((hue + 28) % 360).toFloat(), 0.62f, 0.30f)
            listOf(top, bottom).forEach { colour ->
                assertTrue("hue $hue reads at ${contrastWithWhite(colour)}", contrastWithWhite(colour) >= 4.5)
            }
        }
    }

    @Test
    fun `initials skip an article and keep two letters`() {
        assertEquals("LQ", initials("The Long Quiet"))
        assertEquals("NM", initials("Night Market"))
        assertEquals("B", initials("Blink"))
        assertEquals("T", initials("The"))
        assertEquals("LL", initials("Late-Night Trilogy".replace("Night", "Life")))
    }

    private fun contrastWithWhite(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        return 1.05 / (luminance + 0.05)
    }
}
