package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews
import com.binge.designsystem.R as DesR

/** The console's text field in each shape its call sites use: plain, with a hint, in error, multi-line and secret. */
class ConsoleTextFieldScreenshotTest {
    @Composable
    private fun Fields(content: @Composable () -> Unit) =
        Surface {
            Column(
                modifier = Modifier.padding(dimensionResource(DesR.dimen.padding_l)),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_l)),
            ) { content() }
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun plain() =
        Fields {
            ConsoleTextField(value = "Living room", onValueChange = {}, label = "Device name")
            ConsoleTextField(value = "", onValueChange = {}, label = "Device name", placeholder = "Pixel 10")
            ConsoleTextField(value = "9", onValueChange = {}, label = "Threads", isError = true, supporting = "Enter 1 to 16")
            ConsoleTextField(value = "body { color: red; }", onValueChange = {}, label = "CSS", singleLine = false, minLines = 3)
        }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun secretMasked() = Fields { ConsoleTextField(value = "hunter2", onValueChange = {}, label = "Password", secret = true) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun secretRevealed() =
        Fields { ConsoleTextField(value = "hunter2", onValueChange = {}, label = "Password", secret = true, initiallyRevealed = true) }
}
