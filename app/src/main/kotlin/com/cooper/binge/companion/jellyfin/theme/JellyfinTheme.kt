package com.cooper.binge.companion.jellyfin.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.binge.designsystem.theme.BingeBrand
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.binge.designsystem.theme.DarkColorScheme
import com.binge.designsystem.theme.LightColorScheme

private val Purple200 = Color(0xFFE3C6EE)
private val Purple300 = Color(0xFFCC9BDD)
private val PurpleBrand = Color(0xFFAA5CC3)
private val Purple700 = Color(0xFF7E3A95)
private val Purple900 = Color(0xFF3E1A4A)
private val PurpleInk = Color(0xFF240B2C)
private val Blue200 = Color(0xFFB3E5F7)
private val Blue400 = Color(0xFF33B6E3)
private val Blue900 = Color(0xFF00405A)

val JellyfinDarkColorScheme: ColorScheme =
    DarkColorScheme.copy(
        primary = Purple300,
        onPrimary = PurpleInk,
        primaryContainer = Purple900,
        onPrimaryContainer = Purple200,
        inversePrimary = PurpleBrand,
        secondary = Purple300,
        onSecondary = PurpleInk,
        secondaryContainer = Purple900,
        onSecondaryContainer = Purple200,
        tertiary = Blue400,
        tertiaryContainer = Blue900,
        onTertiaryContainer = Blue200,
        primaryFixed = Purple200,
        primaryFixedDim = Purple300,
        onPrimaryFixed = PurpleInk,
        onPrimaryFixedVariant = Purple900,
    )

val JellyfinLightColorScheme: ColorScheme =
    LightColorScheme.copy(
        primary = Purple700,
        onPrimary = Color.White,
        primaryContainer = Purple200,
        onPrimaryContainer = PurpleInk,
        inversePrimary = PurpleBrand,
        secondary = Purple700,
        onSecondary = Color.White,
        secondaryContainer = Purple200,
        onSecondaryContainer = PurpleInk,
        tertiary = Blue900,
        primaryFixed = Purple200,
        primaryFixedDim = Purple300,
        onPrimaryFixed = PurpleInk,
        onPrimaryFixedVariant = Purple900,
    )

/** The design system's brand slot in Jellyfin's purple, in place of Binge's amber. */
val JellyfinBrand = BingeBrand(light = JellyfinLightColorScheme, dark = JellyfinDarkColorScheme)

@Composable
fun JellyfinTheme(content: @Composable () -> Unit) {
    BingeExpressiveTheme(brand = JellyfinBrand, content = content)
}
