package com.cooper.homelab.ephyra.ui

import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.cooper.homelab.ephyra.demo.demoPictureFor
import com.cooper.homelab.ephyra.demo.isDemoUrl
import java.io.IOException

/**
 * Answers a picture asked of the demo server by drawing it, in place of the network: the demo has no host to reach, and the design
 * system draws its pictures with its own image loader, which the demo transport never sees. It claims only the demo's address, so a
 * real server's picture is never handed to it. A picture the demo deliberately has none of (a person with no photo, a box set's
 * backdrop) fails here, as a missing picture does, so the screen shows its placeholder and nothing goes out to the network.
 */
internal class DemoImageFetcher(
    private val url: String,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val picture = demoPictureFor(url) ?: throw IOException("The demo has no picture for this address")
        return ImageFetchResult(image = drawDemoPicture(picture).asImage(), isSampled = false, dataSource = DataSource.MEMORY)
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? = data.toString().takeIf { it.isDemoUrl() }?.let(::DemoImageFetcher)
    }
}
