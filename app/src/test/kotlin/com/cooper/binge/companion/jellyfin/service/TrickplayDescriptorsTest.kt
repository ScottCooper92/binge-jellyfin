package com.cooper.binge.companion.jellyfin.service

import com.cooper.binge.companion.jellyfin.server.TrickplayInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which set of seek thumbnails the descriptor is made from. */
class TrickplayDescriptorsTest {
    private fun set(width: Int) = TrickplayInfo(width, width * 9 / 16, 10, 10, 360, 10_000L)

    private fun chosen(vararg widths: Int) = TrickplayDescriptors.choose(widths.map(::set))?.width

    @Test
    fun `the smallest set at least the wanted width is chosen`() {
        assertEquals(320, chosen(160, 320, 640))
        assertEquals(480, chosen(640, 480, 160))
    }

    @Test
    fun `when every set is narrower than wanted, the widest is chosen`() {
        assertEquals(240, chosen(160, 240))
    }

    @Test
    fun `a single set is the one chosen, whatever its width`() {
        assertEquals(1_280, chosen(1_280))
        assertEquals(96, chosen(96))
    }

    @Test
    fun `no sets choose nothing`() {
        assertNull(TrickplayDescriptors.choose(emptyList()))
    }
}
