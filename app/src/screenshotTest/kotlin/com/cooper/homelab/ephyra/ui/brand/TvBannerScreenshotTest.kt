package com.cooper.homelab.ephyra.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.R

/** The TV banner at its real size (320x180), at 2x, and at half size, where the wordmark has to stay legible. */
class TvBannerScreenshotTest {
    @PreviewTest
    @Preview(name = "banner-1x", widthDp = 320, heightDp = 180)
    @Preview(name = "banner-2x", widthDp = 640, heightDp = 360)
    @Preview(name = "banner-half", widthDp = 160, heightDp = 90)
    @Composable
    fun banner() =
        Image(
            painterResource(R.drawable.tv_banner),
            contentDescription = null,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
}
