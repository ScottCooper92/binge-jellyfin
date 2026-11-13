package com.cooper.binge.companion.jellyfin.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStateLightPreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

/** The About group: the app's version, the privacy policy, the licences and the beta, as the home and the account show it. */
class AboutGroupScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinScreenStateLightPreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun about() = Surface { AboutGroup(Actions()) }
}
