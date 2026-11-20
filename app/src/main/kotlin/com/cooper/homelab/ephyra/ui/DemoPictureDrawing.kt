package com.cooper.homelab.ephyra.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.cooper.homelab.ephyra.demo.DemoPicture
import com.cooper.homelab.ephyra.demo.DemoPictureShape
import kotlin.math.min

private const val LABEL_WITH_CAPTION_CENTRE = 0.44f
private const val LABEL_CENTRE = 0.5f
private const val CAPTION_CENTRE = 0.8f
private const val POSTER_LABEL_SIZE = 0.3f
private const val WIDE_LABEL_SIZE = 0.34f
private const val PERSON_LABEL_SIZE = 0.4f
private const val CAPTION_SIZE = 0.075f
private const val CAPTION_WIDTH = 0.84f
private const val ELLIPSIS = "…"
private const val CAPTION_ALPHA = 0xD0

/**
 * Draws [picture] as a gradient between its two colours with its label in white, big and centred, and its title small under it.
 * The colours are dark enough for white to read whichever theme the screen is in, so nothing here asks for the theme. A person's is
 * the whole square: the screen crops it to a circle.
 */
internal fun drawDemoPicture(picture: DemoPicture): Bitmap {
    val width = picture.width.toFloat()
    val height = picture.height.toFloat()
    val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, width, height, picture.topColor, picture.bottomColor, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width, height, paint)
    paint.shader = null
    paint.color = Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    val labelSize =
        when (picture.shape) {
            DemoPictureShape.Poster -> POSTER_LABEL_SIZE * min(width, height)
            DemoPictureShape.Wide, DemoPictureShape.Still -> WIDE_LABEL_SIZE * height
            DemoPictureShape.Person -> PERSON_LABEL_SIZE * width
        }
    paint.textSize = labelSize
    val caption = picture.caption
    canvas.drawText(
        picture.label,
        width / 2,
        centredBaseline(
            paint,
            height * if (caption != null) LABEL_WITH_CAPTION_CENTRE else LABEL_CENTRE,
        ),
        paint,
    )
    if (caption != null) {
        paint.typeface = Typeface.DEFAULT
        paint.textSize = CAPTION_SIZE * width
        paint.alpha = CAPTION_ALPHA
        canvas.drawText(fitted(paint, caption, width * CAPTION_WIDTH), width / 2, centredBaseline(paint, height * CAPTION_CENTRE), paint)
    }
    return bitmap
}

/** The baseline that puts a line of text's middle at [centre]. */
private fun centredBaseline(
    paint: Paint,
    centre: Float,
): Float = centre - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2

/** [text] cut to fit [limit] pixels, with an ellipsis where it was cut. */
private fun fitted(
    paint: Paint,
    text: String,
    limit: Float,
): String {
    if (paint.measureText(text) <= limit) return text
    val fits = paint.breakText(text, true, limit - paint.measureText(ELLIPSIS), null)
    return text.take(fits).trimEnd() + ELLIPSIS
}
