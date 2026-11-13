package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinTvScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinTvStressPreviews

/** The same question on the television, its main state at large fonts and in Spanish too. */
class TvConsentScreenshotTest {
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun consent() = TvConsentScreen(onChoice = {})
}
