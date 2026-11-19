package com.cooper.homelab.ephyra.ui.consent

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.homelab.core.uiandroid.consent.ConsentScreen
import com.cooper.homelab.ephyra.preview.JellyfinFontScalePreviews
import com.cooper.homelab.ephyra.preview.JellyfinScreenStatePreview
import com.cooper.homelab.ephyra.preview.JellyfinSpanishPreviews
import com.cooper.homelab.ephyra.ui.Actions
import com.cooper.homelab.ephyra.ui.AppGroup
import com.cooper.homelab.ephyra.ui.AppSettings

/** The question asked before sign-in and the home: what is shared, what never is, and the two answers. */
class ConsentScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
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

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appGroupClientOn() = AppGroup(AppSettings(client = true), Actions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun appGroupClientOff() = AppGroup(AppSettings(client = false), Actions())
}
