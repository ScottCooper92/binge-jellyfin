package com.cooper.homelab.ephyra.ui.client

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.playback.AudioSwitchFailure
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews

/** What Play says where it cannot begin (the demo, and a server reached over plain http at a public address), and when the audio did not change. */
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

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinSpanishFontScalePreviews
    @Composable
    fun audioUnreachable() = AudioSwitchMessage(AudioSwitchFailure.Unreachable, onDismiss = {}, inWindow = false)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @JellyfinSpanishFontScalePreviews
    @Composable
    fun audioRefused() = AudioSwitchMessage(AudioSwitchFailure.Refused, onDismiss = {}, inWindow = false)
}
