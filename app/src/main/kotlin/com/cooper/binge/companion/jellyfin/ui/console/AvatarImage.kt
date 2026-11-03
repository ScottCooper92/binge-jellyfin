package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import com.binge.companion.sdk.attempt
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** The side of the square picture sent to the server, in pixels. */
internal const val AVATAR_SIDE = 512

private const val AVATAR_QUALITY = 90

/**
 * [source], an image file as the user picked it, as the square JPEG the server keeps: the middle square cropped out, and
 * scaled down to [side] pixels (never up). Null when it is not an image this device can decode. The file is read at a reduced
 * size first, so a large photo never sits in memory whole.
 */
internal fun squareAvatar(
    source: ByteArray,
    side: Int = AVATAR_SIDE,
): ByteArray? =
    attempt {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val shorter = minOf(bounds.outWidth, bounds.outHeight)
        if (shorter <= 0) return@attempt null
        var sample = 1
        while (shorter / (sample * 2) >= side) sample *= 2
        val decoded =
            BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@attempt null
        val edge = minOf(decoded.width, decoded.height)
        val upright = exifRotation(source)
        val square =
            Bitmap.createBitmap(
                decoded,
                (decoded.width - edge) / 2,
                (decoded.height - edge) / 2,
                edge,
                edge,
                upright.takeIf { !it.isIdentity },
                true,
            )
        val scaled = if (edge > side) Bitmap.createScaledBitmap(square, side, side, true) else square
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, it) }.toByteArray()
    }.getOrNull()

/** The turn that makes [source] upright, from its EXIF `Orientation`: the re-encoded JPEG carries no EXIF of its own. */
internal fun exifRotation(source: ByteArray): Matrix {
    val orientation =
        attempt {
            ExifInterface(ByteArrayInputStream(source)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
    return Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(ROTATE_90)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(ROTATE_180)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(ROTATE_270)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                postRotate(ROTATE_90)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                postRotate(ROTATE_270)
                postScale(-1f, 1f)
            }
        }
    }
}

private const val ROTATE_90 = 90f
private const val ROTATE_180 = 180f
private const val ROTATE_270 = 270f
