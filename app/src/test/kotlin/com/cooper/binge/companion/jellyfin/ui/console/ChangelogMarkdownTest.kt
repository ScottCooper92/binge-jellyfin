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
        val handler = WebLinksOnly(handlerOf { opened += it })
        handler.openUri("https://example.com/a")
        handler.openUri("file:///data/x")
        handler.openUri("tel:123")
        assertEquals(listOf("https://example.com/a"), opened)
    }

    @Test
    fun `a link no app can open does not crash`() {
        WebLinksOnly(handlerOf { error("no browser") }).openUri("https://example.com")
    }

    private fun handlerOf(onOpen: (String) -> Unit) =
        object : UriHandler {
            override fun openUri(uri: String) = onOpen(uri)
        }

    @Test
    fun `an image becomes its alt text, and a link is left alone`() {
        assertEquals("A chart", withoutImages("![A chart](https://tracker.example/chart.png)"))
        assertEquals("", withoutImages("![](https://tracker.example/pixel.png)"))
        assertEquals("see A chart here", withoutImages("see ![A chart][c] here\n\n[c]: https://x.example/c.png".substringBefore("\n\n")))
        assertEquals("a [link](https://x.example) and logo", withoutImages("a [link](https://x.example) and ![logo](u)"))
        assertEquals("no images, just **text**", withoutImages("no images, just **text**"))
    }
}
