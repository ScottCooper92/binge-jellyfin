package com.cooper.homelab.ephyra.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.ephyra.preview.JellyfinTvScreenPreviews
import com.cooper.homelab.ephyra.preview.JellyfinTvStressPreviews
import com.cooper.homelab.ephyra.ui.Actions
import com.cooper.homelab.ephyra.ui.previewSignedIn

/** The same question on the television, its main state at large fonts and in Spanish too. */
class TvConsentScreenshotTest {
    @PreviewTest
    @JellyfinTvScreenPreviews
    @JellyfinTvStressPreviews
    @Composable
    fun consent() = TvConsentScreen(onChoice = {})

    /** The board with Report a bug focused: its pane is the code the phone scans. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun reportBug() = TvJellyfinRoot(previewSignedIn, Actions(), describing = REPORT_BUG_KEY)

    /** The board with Share usage data focused, both switches in their default off state. */
    @PreviewTest
    @JellyfinTvScreenPreviews
    @Composable
    fun thisApp() = TvJellyfinRoot(previewSignedIn, Actions(), describing = USAGE_DATA_KEY)
}
