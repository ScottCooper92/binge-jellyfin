package com.cooper.homelab.ephyra.openitem

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cooper.homelab.ephyra.server.IndexType
import com.cooper.homelab.ephyra.toOpenItemRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The intent a caller builds, as the exported entry reads it. The extra names are the public contract: this pins them. */
@RunWith(AndroidJUnit4::class)
class OpenItemIntentTest {
    private val id = "0123456789abcdef0123456789abcdef"

    private fun intent() = Intent(OpenItem.ACTION)

    @Test
    fun `the action and the extra names are the published contract`() {
        assertEquals("com.cooper.homelab.ephyra.action.OPEN_ITEM", OpenItem.ACTION)
        assertEquals("com.cooper.homelab.ephyra.extra.SERVER_URL", OpenItem.EXTRA_SERVER_URL)
        assertEquals("com.cooper.homelab.ephyra.extra.ITEM_ID", OpenItem.EXTRA_ITEM_ID)
        assertEquals("com.cooper.homelab.ephyra.extra.TMDB_TYPE", OpenItem.EXTRA_TMDB_TYPE)
        assertEquals("com.cooper.homelab.ephyra.extra.TMDB_ID", OpenItem.EXTRA_TMDB_ID)
    }

    @Test
    fun `an intent with every extra is read`() {
        val request =
            intent()
                .putExtra(OpenItem.EXTRA_SERVER_URL, "https://jelly.example/")
                .putExtra(OpenItem.EXTRA_ITEM_ID, id)
                .putExtra(OpenItem.EXTRA_TMDB_TYPE, "tv")
                .putExtra(OpenItem.EXTRA_TMDB_ID, 1399)
                .toOpenItemRequest()!!
        assertEquals(serverKey("https://jelly.example"), request.serverKey)
        assertEquals(id, request.itemId)
        assertEquals(TmdbRef(IndexType.SERIES, 1399), request.tmdb)
    }

    @Test
    fun `an intent with no extras is no request`() {
        assertNull(intent().toOpenItemRequest())
    }

    @Test
    fun `extras of the wrong type are ignored and never crash`() {
        val request =
            intent()
                .putExtra(OpenItem.EXTRA_SERVER_URL, 42)
                .putExtra(OpenItem.EXTRA_ITEM_ID, longArrayOf(1, 2))
                .putExtra(OpenItem.EXTRA_TMDB_TYPE, true)
                .putExtra(OpenItem.EXTRA_TMDB_ID, "603")
        assertNull(request.toOpenItemRequest())
    }

    @Test
    fun `an id that is not positive is ignored`() {
        val request = intent().putExtra(OpenItem.EXTRA_TMDB_TYPE, "movie").putExtra(OpenItem.EXTRA_TMDB_ID, -3)
        assertNull(request.toOpenItemRequest())
    }
}
