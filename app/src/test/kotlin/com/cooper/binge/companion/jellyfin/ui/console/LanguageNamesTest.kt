package com.cooper.binge.companion.jellyfin.ui.console

import android.os.LocaleList
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LanguageNamesTest {
    private val english = Culture("English", "en", listOf("eng"))
    private val french = Culture("French", "fr", listOf("fre", "fra"))
    private val klingon = Culture("Klingon", "", listOf("tlh"))
    private val canadian = Culture("French (Canada)", "fr-ca", listOf("frc"))
    private val unknown = Culture("Elvish", "qq", listOf("qqq"))
    private val cultures = listOf(english, french, klingon)

    @Test
    fun `a language is named in the reader's own language where Android knows it`() {
        assertEquals("Francés", french.label(Locale.forLanguageTag("es")))
        assertEquals("French", french.label(Locale.UK))
        assertEquals("Klingon", klingon.label(Locale.forLanguageTag("es")))
        assertEquals("Elvish", unknown.label(Locale.UK))
    }

    @Test
    fun `a regional variant is named with its region`() {
        assertEquals("French (Canada)", canadian.label(Locale.UK))
        assertEquals("Francés (Canadá)", canadian.label(Locale.forLanguageTag("es")))
        assertEquals("FRC", canadian.mark())
    }

    @Test
    fun `its mark is the two-letter code, else the three`() {
        assertEquals("FR", french.mark())
        assertEquals("TLH", klingon.mark())
    }

    @Test
    fun `a stored code finds its language under any of its names`() {
        assertEquals(french, cultures.find("fra"))
        assertEquals("fre", cultures.find("fra")?.code)
        assertNull(cultures.find("xyz"))
    }

    @Test
    fun `the device's languages are suggested in its order, once each`() {
        val locales = LocaleList(Locale.FRANCE, Locale.UK, Locale.CANADA_FRENCH, Locale.forLanguageTag("de"))
        assertEquals(listOf("fre", "eng"), deviceLanguages(cultures, locales))
    }

    @Test
    fun `the popular languages follow the device's, each once, only those the server offers`() {
        val locales = LocaleList(Locale.FRANCE)
        val suggested = suggestedLanguages(cultures, locales)
        assertEquals("fre", suggested.first())
        assertEquals(suggested.distinct(), suggested)
        assertTrue("eng" in suggested)
        assertTrue(suggested.all { code -> cultures.any { it.code == code } })
    }

    @Test
    fun `suggestions keep the device's order first and drop what isn't offered`() {
        assertEquals(listOf("GB", "US", "DE"), suggestions(listOf("GB", "XX"), listOf("US", "GB", "DE"), setOf("GB", "US", "DE")))
    }

    @Test
    fun `a subtitle mode the app doesn't know reads as the server's default`() {
        assertEquals(SubtitleMode.Smart, SubtitleMode.of("Smart"))
        assertEquals(SubtitleMode.Default, SubtitleMode.of("Sometimes"))
        assertEquals(SubtitleMode.Default, SubtitleMode.of(null))
    }
}
