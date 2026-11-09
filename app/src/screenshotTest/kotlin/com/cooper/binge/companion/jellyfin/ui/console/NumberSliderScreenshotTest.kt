package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

@Composable
private fun RefreshSheet(days: Int?) {
    val resources = LocalContext.current.resources
    Surface {
        NumberSliderContent(
            title = stringResource(R.string.library_refresh),
            value = days,
            range = 1..90,
            format = { resources.getQuantityString(R.plurals.library_every_days, it, it) },
            openEndLabel = stringResource(R.string.library_never),
            onSave = {},
        )
    }
}

/** The number slider's sheet, as a library's refresh interval: a value on the scale, never, one beyond the scale, at large fonts and in Spanish. */
class NumberSliderScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun refreshThirtyDays() = RefreshSheet(30)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun refreshNever() = RefreshSheet(null)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun refreshBeyondTheScale() = RefreshSheet(120)
}
