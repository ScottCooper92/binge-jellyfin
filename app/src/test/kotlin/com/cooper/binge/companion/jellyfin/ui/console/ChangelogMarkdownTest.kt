package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.ui.platform.UriHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangelogMarkdownTest {
    @Test
    fun `only http and https addresses are web links`() {
        assertTrue(isWebLink("https://github.com/x/y/releases"))
        assertTrue(isWebLink("HTTP://example.com"))
        assertFalse(isWebLink("javascript:alert(1)"))
        assertFalse(isWebLink("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebLink("file:///sdcard/a"))
        assertFalse(isWebLink("content://media/1"))
        assertFalse(isWebLink("#heading"))
    }

    @Test
    fun `a tap opens a web link and nothing else`() {
        val opened = mutableListOf<String>()
        val handler = WebLinksOnly(UriHandler { opened += it })
        handler.openUri("https://example.com/a")
        handler.openUri("file:///data/x")
        handler.openUri("tel:123")
        assertEquals(listOf("https://example.com/a"), opened)
    }

    @Test
    fun `a link no app can open does not crash`() {
        WebLinksOnly(UriHandler { error("no browser") }).openUri("https://example.com")
    }
}
