package com.cooper.binge.companion.jellyfin.ui.console

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.preview.JellyfinFontScalePreviews
import com.cooper.binge.companion.jellyfin.preview.JellyfinScreenStatePreview
import com.cooper.binge.companion.jellyfin.preview.JellyfinSpanishPreviews

@Composable
private fun SessionsSheet(sessions: Int?) {
    val resources = LocalContext.current.resources
    Surface {
        NumberSliderContent(
            title = stringResource(R.string.users_max_sessions),
            value = sessions,
            range = 1..20,
            format = { resources.getQuantityString(R.plurals.users_sessions_at_once, it, it) },
            openEndLabel = stringResource(R.string.users_no_limit),
            onSave = {},
        )
    }
}

@Composable
private fun BitrateSheet(kilobits: Int?) {
    val resources = LocalContext.current.resources
    Surface {
        NumberSliderContent(
            title = stringResource(R.string.users_bitrate),
            value = kilobits,
            range = 1_000..100_000,
            format = { bitrateText(resources, it * 1_000) },
            openEndLabel = stringResource(R.string.users_no_limit),
            onSave = {},
            step = 1_000,
        )
    }
}

@Composable
private fun LockoutSheet(attempts: Int?) {
    val resources = LocalContext.current.resources
    Surface {
        NumberSliderContent(
            title = stringResource(R.string.users_lockout),
            value = attempts,
            range = 1..20,
            format = { lockoutText(resources, it) },
            openEndLabel = stringResource(R.string.users_lockout_never),
            onSave = {},
        )
    }
}

/** The three sliders of a user's policy: sessions at once, the bitrate limit away from home, and the sign-in lockout. */
class UserPolicySliderScreenshotTest {
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun sessionsThree() = SessionsSheet(3)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun sessionsNoLimit() = SessionsSheet(null)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun bitrateEight() = BitrateSheet(8_000)

    /** A stored 2.5 Mbps sits between two steps and reads as stored. */
    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinFontScalePreviews
    @JellyfinSpanishPreviews
    @Composable
    fun bitrateBetweenSteps() = BitrateSheet(2_500)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun bitrateNoLimit() = BitrateSheet(null)

    @PreviewTest
    @JellyfinScreenStatePreview
    @JellyfinSpanishPreviews
    @Composable
    fun lockoutFive() = LockoutSheet(5)

    @PreviewTest
    @JellyfinScreenStatePreview
    @Composable
    fun lockoutNever() = LockoutSheet(null)
}
