package com.cooper.binge.companion.jellyfin.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.binge.companion.sdk.readBrandColor
import com.binge.designsystem.theme.DarkColorScheme
import com.binge.designsystem.theme.LightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val NON_TEXT_CONTRAST = 3.0
private const val LUMINANCE_OFFSET = 0.05

/**
 * The brand colour the LIBRARY service declares for Binge to tint with: read the way the host reads it, and held to
 * the contract's 3:1 non-text rule against Binge's own surfaces, so a later edit cannot drop under it unnoticed.
 */
@RunWith(RobolectricTestRunner::class)
class BrandColorManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun declared(dark: Boolean): Int? {
        val service =
            context.packageManager.getServiceInfo(
                ComponentName(context, JellyfinCompanionService::class.java),
                PackageManager.GET_META_DATA,
            )
        return service.metaData.readBrandColor(dark) { id -> context.getColor(id) }
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val high = maxOf(a.luminance(), b.luminance()).toDouble()
        val low = minOf(a.luminance(), b.luminance()).toDouble()
        return (high + LUMINANCE_OFFSET) / (low + LUMINANCE_OFFSET)
    }

    @Test
    fun `the service declares a purple for a light theme and a lighter one for a dark theme`() {
        assertEquals(0xFFAA5CC3.toInt(), declared(dark = false))
        assertEquals(0xFFCC9BDD.toInt(), declared(dark = true))
    }

    @Test
    fun `the dark colour clears 3 to 1 on every dark surface Binge draws`() {
        val colour = Color(requireNotNull(declared(dark = true)))
        val surfaces =
            listOf(
                DarkColorScheme.background,
                DarkColorScheme.surface,
                DarkColorScheme.surfaceContainerLowest,
                DarkColorScheme.surfaceContainerLow,
                DarkColorScheme.surfaceContainer,
                DarkColorScheme.surfaceContainerHigh,
                DarkColorScheme.surfaceContainerHighest,
            )
        surfaces.forEach { assertTrue("$it", contrast(colour, it) >= NON_TEXT_CONTRAST) }
    }

    @Test
    fun `the plain colour clears 3 to 1 on every light surface Binge draws`() {
        val colour = Color(requireNotNull(declared(dark = false)))
        val surfaces =
            listOf(
                LightColorScheme.background,
                LightColorScheme.surface,
                LightColorScheme.surfaceContainerLowest,
                LightColorScheme.surfaceContainerLow,
                LightColorScheme.surfaceContainer,
                LightColorScheme.surfaceContainerHigh,
                LightColorScheme.surfaceContainerHighest,
            )
        surfaces.forEach { assertTrue("$it", contrast(colour, it) >= NON_TEXT_CONTRAST) }
    }
}
