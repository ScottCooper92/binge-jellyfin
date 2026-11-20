package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/** What Play says where it cannot begin: the demo, and a server reached over plain http at a public address. */
class PlayBlockedScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinSpanishFontScalePreviews
    @Composable
    fun demo() = PlayBlockedMessage(PlayBlock.Demo, onDismiss = {}, inWindow = false)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinSpanishFontScalePreviews
    @Composable
    fun publicHttp() = PlayBlockedMessage(PlayBlock.PublicHttp, onDismiss = {}, inWindow = false)
}
