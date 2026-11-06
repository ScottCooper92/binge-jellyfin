package com.cooper.binge.companion.jellyfin.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.server.ServerUser
import com.cooper.binge.companion.jellyfin.ui.console.LocalUserPictures
import com.cooper.binge.companion.jellyfin.ui.console.UserPictureSource

/** The home with the signed-in user's own picture, as an administrator (top bar and account row) and as anyone else (the row). */
class JellyfinOwnPictureScreenshotTest {
    /** A picture that cannot be mistaken for initials: a green field with a white ring. */
    private val pictures =
        object : UserPictureSource {
            private val picture =
                Bitmap.createBitmap(PICTURE, PICTURE, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.rgb(GREEN_R, GREEN_G, GREEN_B))
                    val ring =
                        Paint().apply {
                            color = Color.WHITE
                            style = Paint.Style.STROKE
                            strokeWidth = RING
                        }
                    canvas.drawCircle(PICTURE / 2f, PICTURE / 2f, PICTURE / 3f, ring)
                }

            override suspend fun read(user: ServerUser): Bitmap? = cached(user)

            // A frame is drawn once, before any read can land, so the picture has to be there on the first frame.
            override fun cached(user: ServerUser): Bitmap? = picture.takeIf { user.imageTag != null }
        }

    @Composable
    private fun Home(state: UiState.SignedIn) =
        CompositionLocalProvider(LocalUserPictures provides pictures) { JellyfinRoot(state, Actions()) }

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @Composable
    fun adminWithPicture() = Home(previewSignedInAdminPicture)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun userWithPicture() = Home(previewSignedInPicture)

    private companion object {
        const val PICTURE = 96
        const val RING = 8f
        const val GREEN_R = 32
        const val GREEN_G = 140
        const val GREEN_B = 96
    }
}
