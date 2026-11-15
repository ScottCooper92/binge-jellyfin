package com.cooper.binge.companion.jellyfin.theme

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JellyfinTvThemeTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    /** A device's TV root has no surface of its own: a `Text` with no colour reads this, and it must be light. */
    @Test
    fun `text that inherits its colour is the light on-background colour`() {
        var inherited = Color.Unspecified
        compose.setContent { JellyfinTvTheme { inherited = LocalContentColor.current } }
        compose.waitForIdle()
        assertEquals(JellyfinTvColorScheme.onBackground, inherited)
    }
}
