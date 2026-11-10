package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

/** A refusal whose server reason is too long for a snackbar: its lead as the title, the whole reason, and OK. */
class RefusedDialogScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun longReason() =
        Surface {
            RefusedDialog(
                title = stringResource(R.string.settings_not_saved, "").trim().trimEnd('.'),
                reason = "Library path /mnt/media/Movies does not exist or is not readable by the server.",
                onDismiss = {},
                inWindow = false,
            )
        }
}
