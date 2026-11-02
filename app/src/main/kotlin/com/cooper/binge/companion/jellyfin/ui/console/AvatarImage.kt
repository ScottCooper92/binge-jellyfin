package com.cooper.binge.companion.jellyfin.ui.console

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.binge.companion.sdk.attempt
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
        val square = Bitmap.createBitmap(decoded, (decoded.width - edge) / 2, (decoded.height - edge) / 2, edge, edge)
        val scaled = if (edge > side) Bitmap.createScaledBitmap(square, side, side, true) else square
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, it) }.toByteArray()
    }.getOrNull()

/** The longest side of a login splash image sent to the server, in pixels. */
internal const val SPLASH_MAX_SIDE = 1920

/**
 * [source], an image file as the user picked it, as the JPEG the server keeps for a wide image: the whole picture, scaled down
 * to fit [maxSide] pixels on its longer side (never up), not cropped. Null when it is not an image this device can decode. Read
 * at a reduced size first, as [squareAvatar] does.
 */
internal fun fittedImage(
    source: ByteArray,
    maxSide: Int = SPLASH_MAX_SIDE,
): ByteArray? =
    attempt {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        if (minOf(bounds.outWidth, bounds.outHeight) <= 0) return@attempt null
        var sample = 1
        while (longer / (sample * 2) >= maxSide) sample *= 2
        val decoded =
            BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return@attempt null
        val edge = maxOf(decoded.width, decoded.height)
        val scaled =
            if (edge > maxSide) {
                val ratio = maxSide.toFloat() / edge
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                decoded
            }
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_QUALITY, it) }.toByteArray()
    }.getOrNull()
