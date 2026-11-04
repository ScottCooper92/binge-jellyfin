package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenPreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

/** A square picture with a bright disc, so a frame shows the crop to a circle. */
internal val profilePictureFixture: Bitmap =
    Bitmap.createBitmap(PICTURE_SIDE, PICTURE_SIDE, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.rgb(64, 96, 160))
        for (x in 0 until PICTURE_SIDE) {
            for (y in 0 until PICTURE_SIDE) {
                val dx = x - PICTURE_SIDE / 2
                val dy = y - PICTURE_SIDE / 2
                if (dx * dx + dy * dy < DISC * DISC) setPixel(x, y, Color.rgb(240, 200, 80))
            }
        }
    }

private const val PICTURE_SIDE = 256
private const val DISC = 80

/** The profile picture page: none and one across the matrix, then each other state, and the remove confirm. */
class ProfilePictureScreenshotTest {
    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun none() = ProfilePictureScreen(ProfilePictureUiState.Ready(), ProfilePictureActions())

    @PreviewTest
    @JellyfinScreenPreviews
    @JellyfinFontScalePreviews
    @Composable
    fun picture() = ProfilePictureScreen(ProfilePictureUiState.Ready(picture = profilePictureFixture), ProfilePictureActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun uploading() = ProfilePictureScreen(ProfilePictureUiState.Ready(picture = profilePictureFixture, busy = true), ProfilePictureActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun unreadable() = ProfilePictureScreen(ProfilePictureUiState.Ready(unreadable = true), ProfilePictureActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun removing() = Surface { RemovePictureDialog(ProfilePictureActions(), inWindow = false) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun loading() = ProfilePictureScreen(ProfilePictureUiState.Loading, ProfilePictureActions())

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun failed() = ProfilePictureScreen(ProfilePictureUiState.Failed(ConsoleFailure.Unreachable), ProfilePictureActions())
}
