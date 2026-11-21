package com.cooper.homelab.ephyra.openitem

import com.cooper.homelab.ephyra.server.IndexType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the exported entry makes of what a caller sends: valid parts kept, everything else dropped, never a crash. */
class OpenItemRequestTest {
    private val id = "0123456789abcdef0123456789abcdef"

    private fun parse(
        url: String? = "http://jelly.lan:8096",
        item: String? = id,
        type: String? = "movie",
        tmdb: Int? = 603,
    ) = OpenItemRequest.parse(url, item, type, tmdb)

    @Test
    fun `a request with every part keeps every part`() {
        val request = parse()!!
        assertEquals("http://jelly.lan:8096", request.serverKey)
        assertEquals(id, request.itemId)
        assertEquals(TmdbRef(IndexType.MOVIE, 603), request.tmdb)
    }

    @Test
    fun `tv is a series and the type is read in any case`() {
        assertEquals(TmdbRef(IndexType.SERIES, 1399), parse(type = " TV ", tmdb = 1399)!!.tmdb)
    }

    @Test
    fun `an item id with dashes and capitals is the server's spelling`() {
        assertEquals(id, parse(item = "01234567-89AB-CDEF-0123-456789ABCDEF")!!.itemId)
    }

    @Test
    fun `a request that names nothing to open is no request`() {
        assertNull(parse(item = null, tmdb = null))
        assertNull(parse(url = null, item = null, type = null, tmdb = null))
        assertNull(parse(item = "not-an-id", type = "movie", tmdb = null))
    }

    @Test
    fun `a bad part is dropped and a good one still opens`() {
        assertNull(parse(item = "zzz")!!.itemId)
        assertEquals(TmdbRef(IndexType.MOVIE, 603), parse(item = "zzz")!!.tmdb)
        assertNull(parse(type = "person")!!.tmdb)
        assertEquals(id, parse(type = "person")!!.itemId)
        assertNull(parse(tmdb = 0)!!.tmdb)
        assertNull(parse(tmdb = -5)!!.tmdb)
        assertNull(parse(tmdb = null)!!.tmdb)
    }

    @Test
    fun `an item id of the wrong length or a non hex digit is not an id`() {
        assertNull(parse(item = id.dropLast(1), tmdb = null))
        assertNull(parse(item = id + "0", tmdb = null))
        assertNull(parse(item = id.replaceRange(0, 1, "g"), tmdb = null))
        assertNull(parse(item = "0".repeat(5000), tmdb = null))
    }

    @Test
    fun `a server address that is not http or https is dropped`() {
        assertNull(parse(url = "ftp://jelly.lan")!!.serverKey)
        assertNull(parse(url = "javascript:alert(1)")!!.serverKey)
        assertNull(parse(url = "file:///etc/passwd")!!.serverKey)
        assertNull(parse(url = "jelly.lan:8096")!!.serverKey)
        assertNull(parse(url = "")!!.serverKey)
        assertNull(parse(url = "http://")!!.serverKey)
        assertNull(parse(url = "http://exa mple.com")!!.serverKey)
        assertNull(parse(url = "http://jelly.lan/" + "a".repeat(3000))!!.serverKey)
    }

    @Test
    fun `two spellings of one address have one key`() {
        val same =
            listOf(
                "http://Jelly.LAN:8096",
                "http://jelly.lan:8096/",
                " http://jelly.lan:8096 ",
                "http://user:pw@jelly.lan:8096?x=1#frag",
            )
        same.forEach { assertEquals(it, "http://jelly.lan:8096", serverKey(it)) }
    }

    @Test
    fun `a default port is the same as none`() {
        assertEquals(serverKey("https://jelly.example"), serverKey("https://jelly.example:443/"))
        assertEquals(serverKey("http://jelly.lan"), serverKey("http://jelly.lan:80"))
    }

    @Test
    fun `a different scheme, host, port or path is a different server`() {
        val base = serverKey("https://jelly.example")
        assertEquals(false, base == serverKey("http://jelly.example"))
        assertEquals(false, base == serverKey("https://other.example"))
        assertEquals(false, base == serverKey("https://jelly.example:8920"))
        assertEquals(false, base == serverKey("https://jelly.example/jellyfin"))
        assertEquals(serverKey("https://jelly.example/jellyfin"), serverKey("https://jelly.example/jellyfin/"))
    }

    @Test
    fun `an address with no scheme is read as http only where it is asked for`() {
        assertNull(serverKey("jelly.lan:8096"))
        assertEquals("http://jelly.lan:8096", serverKey("jelly.lan:8096", assumeHttp = true))
    }
}
