package com.cooper.homelab.ephyra

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What a test says about the fake server's requests when a wait times out: where they went, never what they carried. */
class FakeJellyfinAskedTest {
    private val jellyfin = FakeJellyfin()

    @After
    fun close() = jellyfin.close()

    private fun get(path: String) {
        val request =
            Request
                .Builder()
                .url("${jellyfin.baseUrl}$path")
                .header("Authorization", "MediaBrowser Token=\"secret-token\"")
                .build()
        OkHttpClient().newCall(request).execute().close()
    }

    @Test
    fun `nothing has been asked of a server nobody called`() {
        assertEquals(emptyList<String>(), jellyfin.asked())
    }

    @Test
    fun `each request is its method and its path, in the order it came`() {
        get("/System/Info/Public")
        get("/Users/Public")
        assertEquals(listOf("GET /System/Info/Public", "GET /Users/Public"), jellyfin.asked())
    }

    @Test
    fun `no query and no header reach the text`() {
        get("/Items?api_key=secret-token&searchTerm=private")
        val text = jellyfin.asked().toString()
        assertEquals(listOf("GET /Items"), jellyfin.asked())
        assertFalse(text, text.contains("secret-token"))
        assertFalse(text, text.contains("private"))
    }
}
