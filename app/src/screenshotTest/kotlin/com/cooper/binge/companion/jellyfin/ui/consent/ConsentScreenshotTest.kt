package com.cooper.binge.companion.jellyfin.ui.consent

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.AppGroup
import com.cooper.binge.companion.jellyfin.ui.AppSettings

/** The question asked before sign-in and the home: what is shared, what never is, and the two answers. */
class ConsentScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun consent() = ConsentScreen(onChoice = {})

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appGroupDefaults() = AppGroup(AppSettings(), Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appGroupSharing() = AppGroup(AppSettings(shareUsageData = true, sendCrashReports = false), Actions())
}
