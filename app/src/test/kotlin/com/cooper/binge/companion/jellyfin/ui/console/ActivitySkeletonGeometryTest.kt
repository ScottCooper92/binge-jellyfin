package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.testing.assertSkeletonReservesGeometry
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ActivityEntry
import com.cooper.binge.companion.jellyfin.server.Severity
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The activity page's skeleton reserves the place its first day's group fills, so the page does not jump when the log arrives. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ActivitySkeletonGeometryTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val entry = ActivityEntry(1, "alice signed in", "192.168.1.5", "SessionStarted", 1_700_000_000_000L, Severity.Information)

    @Test
    fun `the skeleton holds the first group where the page puts it`() {
        compose.assertSkeletonReservesGeometry(
            listOf(LayoutAnchors.section(LayoutAnchors.Collection.FIRST_ITEM)),
            checkStartEdge = true,
        ) { resolved ->
            JellyfinTheme {
                ActivityScreen(
                    state = if (resolved) ActivityUiState.Ready(listOf(entry), 1) else ActivityUiState.Loading,
                    actions = ActivityActions(),
                )
            }
        }
    }
}
