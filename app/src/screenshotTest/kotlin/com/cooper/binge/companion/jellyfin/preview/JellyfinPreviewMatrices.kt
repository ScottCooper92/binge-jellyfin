package com.cooper.binge.companion.jellyfin.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.binge.designsystem.preview.FOLDABLE_PORTRAIT_SPEC
import com.binge.designsystem.preview.PHONE_LANDSCAPE_SPEC
import com.binge.designsystem.preview.PHONE_PORTRAIT_SPEC
import com.binge.designsystem.preview.SOURCE_LOCALE
import com.binge.designsystem.preview.ScreenshotThemeWrapper
import com.binge.designsystem.theme.BingeBrand
import com.cooper.binge.companion.jellyfin.theme.JellyfinBrand

/*
 * This app's multipreviews: the design system's cells, wearing [JellyfinBrand] instead of Binge's.
 *
 * A multipreview binds its theme wrapper by class, so an app with its own brand subclasses the
 * wrapper and declares its own annotations from the design system's public specs and [SOURCE_LOCALE],
 * as binge-archive does.
 */

/** The phone harness theme, wearing [JellyfinBrand]. */
class JellyfinScreenshotThemeWrapper : ScreenshotThemeWrapper() {
    override val brand: BingeBrand = JellyfinBrand
}

/** A screen's layout: phone dark and light, phone on its side, and the unfolded foldable. */
@PreviewWrapper(JellyfinScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "phone-light", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_NO, locale = SOURCE_LOCALE)
@Preview(name = "phone-land", device = PHONE_LANDSCAPE_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "foldable", device = FOLDABLE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class JellyfinScreenPreviews

/** A phone window at font scales 1.5 and 2.0; the 1.0 cell is the screen's own frame. */
@PreviewWrapper(JellyfinScreenshotThemeWrapper::class)
@Preview(name = "font15", device = PHONE_PORTRAIT_SPEC, fontScale = 1.5f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
@Preview(name = "font20", device = PHONE_PORTRAIT_SPEC, fontScale = 2f, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class JellyfinFontScalePreviews

/** One phone cell in Spanish, whose copy runs longer than English. */
@PreviewWrapper(JellyfinScreenshotThemeWrapper::class)
@Preview(name = "es", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = "es")
annotation class JellyfinSpanishPreviews

/** The phone cell alone, for a state of a screen whose layout frame has the matrix. */
@PreviewWrapper(JellyfinScreenshotThemeWrapper::class)
@Preview(name = "phone", device = PHONE_PORTRAIT_SPEC, uiMode = UI_MODE_NIGHT_YES, locale = SOURCE_LOCALE)
annotation class JellyfinScreenStatePreview
