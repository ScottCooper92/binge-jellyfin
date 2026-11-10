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
private fun IntervalSheet(millis: Int) {
    val resources = LocalContext.current.resources
    Surface {
        NumberSliderContent(
            title = stringResource(R.string.trickplay_interval),
            value = millis,
            range = 1_000..60_000,
            format = { intervalText(resources, it) },
            openEndLabel = null,
            onSave = {},
            step = 1_000,
        )
    }
}

/** The trickplay interval's sheet: a whole number of seconds, one between two seconds, and one beyond a minute. */
class TrickplayIntervalScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun intervalTen() = IntervalSheet(10_000)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun intervalBetweenSteps() = IntervalSheet(2_500)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun intervalBeyondScale() = IntervalSheet(90_000)
}
