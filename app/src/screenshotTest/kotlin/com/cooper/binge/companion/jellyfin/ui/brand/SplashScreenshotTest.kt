package com.cooper.binge.companion.jellyfin.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview

/**
 * The frame the splash animation ends on: the launcher foreground the animated vector sweeps to, on the
 * splash ground of the mode (light, and dark from values-night). The sweep itself is not a frame.
 */
class SplashScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @Composable
    fun splashRest() =
        Box(
            Modifier.fillMaxSize().background(colorResource(R.color.splash_background)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(240.dp))
        }
}
