package com.cooper.homelab.ephyra.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.toBitmap
import coil3.toUri
import com.cooper.homelab.ephyra.demo.DEMO_ORIGIN
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.IOException

/** The demo's pictures are drawn by one fetcher that claims the demo's address and nothing else. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DemoImageFetcherTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val loader = ImageLoader(context)

    private fun fetcher(url: String) = DemoImageFetcher.Factory().create(url.toUri(), Options(context), loader)

    private fun demo(
        id: String,
        kind: String = "Primary",
        width: Int = 300,
    ) = "$DEMO_ORIGIN/Items/$id/Images/$kind?tag=p-$id&maxWidth=$width"

    @Test
    fun `a real server's picture never reaches the fetcher`() {
        listOf(
            "https://jellyfin.example.com/Items/demo-movie-1/Images/Primary?tag=p&maxWidth=300",
            "http://192.168.1.10:8096/Items/m1/Images/Backdrop?maxWidth=1080",
            "https://demo.example.com/Items/demo-movie-1/Images/Primary",
            "https://image.tmdb.org/t/p/w500/poster.jpg",
        ).forEach { assertNull(it, fetcher(it)) }
    }

    @Test
    fun `the demo's address is claimed, drawn at the size asked for`() =
        runBlocking {
            val result = fetcher(demo("demo-movie-1"))!!.fetch() as ImageFetchResult
            assertEquals(300, result.image.width)
            assertEquals(450, result.image.height)
            val bitmap = result.image.toBitmap()
            // Opaque, with the gradient's colour in the corner and the label's white near the middle of the poster.
            assertTrue(bitmap.getPixel(2, 2) ushr 24 == 0xFF)
            val white = (170 until 230).sumOf { y -> (0 until 300).count { x -> (0xFF and (bitmap.getPixel(x, y) shr 8)) > 0xE0 } }
            assertTrue("the label is drawn in white: $white pixels", white > 100)
        }

    @Test
    fun `each kind of picture has its own shape`() =
        runBlocking {
            suspend fun size(url: String) = (fetcher(url)!!.fetch() as ImageFetchResult).image.let { it.width to it.height }
            assertEquals(300 to 450, size(demo("demo-movie-1")))
            assertEquals(640 to 360, size(demo("demo-movie-1", "Backdrop", 640)))
            assertEquals(320 to 180, size(demo("demo-ep-4-s1-5", width = 320)))
            assertEquals(96 to 96, size(demo("demo-person-1", width = 96)))
        }

    @Test
    fun `a picture the demo leaves out fails, so the placeholder shows and nothing goes to the network`() {
        listOf(demo("demo-person-3"), demo("demo-box-1", "Backdrop"), demo("nobody")).forEach { url ->
            // The demo's address is claimed, so no network fetcher is tried.
            val claimed = fetcher(url) ?: error("$url should be claimed by the demo fetcher")
            try {
                runBlocking { claimed.fetch() }
                fail("$url should have no picture")
            } catch (expected: IOException) {
                assertNotNull(expected.message)
            }
        }
    }

    @Test
    fun `the image loader the app builds carries the fetcher and still has the default ones`() {
        val loader =
            com.cooper.homelab.ephyra
                .JellyfinApp()
                .newImageLoader(context)
        assertTrue(loader.components.fetcherFactories.any { it.first is DemoImageFetcher.Factory })
        assertTrue(loader.components.fetcherFactories.size > 1)
    }
}
