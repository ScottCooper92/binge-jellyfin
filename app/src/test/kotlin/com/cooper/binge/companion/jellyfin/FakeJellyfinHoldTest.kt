package com.cooper.binge.companion.jellyfin

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** The fake's hold: a request stays in flight until the test ends it, and only that one request is held. */
class FakeJellyfinHoldTest {
    private val jellyfin = FakeJellyfin()
    private val http = OkHttpClient()
    private val pool = Executors.newCachedThreadPool()

    @After
    fun close() {
        pool.shutdownNow()
        jellyfin.close()
    }

    private fun call(
        path: String,
        method: String = "GET",
    ): Future<Int> =
        pool.submit<Int> {
            val builder = Request.Builder().url("${jellyfin.baseUrl}$path")
            if (method == "POST") builder.post(okhttp3.RequestBody.create(null, ByteArray(0))) else builder.get()
            http.newCall(builder.build()).execute().use { it.code }
        }

    @Test
    fun `a held request waits, then answers as the server would when released`() {
        val held = jellyfin.hold("/System/Info")
        val answer = call("/System/Info")
        held.awaitArrival()
        assertFalse(answer.isDone)
        held.release()
        assertEquals(200, answer.get())
    }

    @Test
    fun `a held request can be refused with a status, or cut off`() {
        val refused = jellyfin.hold("/System/Info")
        val answer = call("/System/Info")
        refused.awaitArrival()
        refused.refuse(409, """{"title":"No space"}""")
        assertEquals(409, answer.get())
        val cut = jellyfin.hold("/System/Info")
        val dropped = call("/System/Info")
        cut.awaitArrival()
        cut.cut()
        val failure = runCatching { dropped.get() }.exceptionOrNull()
        assertTrue(failure?.cause is IOException)
    }

    @Test
    fun `only the held request is held, and a method filter holds only that method`() {
        val held = jellyfin.hold("/System/Info", method = "POST")
        // A GET to the same path goes through while the POST is the one held.
        assertEquals(200, call("/System/Info").get())
        val post = call("/System/Info", "POST")
        held.awaitArrival()
        held.release()
        post.get()
        // The next request to the path is not held.
        assertEquals(200, call("/System/Info").get())
    }
}
