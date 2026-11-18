package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.ThisAppScreen
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.presentation.diagnostics.DiagnosticsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

/** The Diagnostics page opens from This app, and what it carries above the log is the app's and the device's alone. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticsRouteTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    @Before
    fun main() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun `the Diagnostics row in This app opens the Diagnostics page`() {
        val opened = mutableListOf<ConsolePage>()
        compose.setContent { JellyfinTheme { ThisAppScreen(Actions(onOpenPage = { opened += it }), onBack = {}) } }
        compose.onNodeWithText("Diagnostics").performScrollTo().performClick()
        assertEquals(listOf(ConsolePage.Diagnostics), opened)
    }

    @Test
    fun `the lines above the log hold the app and the device and nothing about a server`() {
        val log = RingLog(Clock { Instant.fromEpochMilliseconds(0) }, Redactor(), serverOrder = { emptyList() }, ownPackages = emptyList())
        val host = DiagnosticsHost(log)
        val lines = (host.model.state.value as DiagnosticsState.Empty).preamble
        assertEquals(listOf("App: ", "Android: ", "Device: "), lines.map { it.substringBefore(':') + ": " })
        assertTrue(lines.none { it.contains("http", ignoreCase = true) || it.contains("://") })
        assertFalse(lines.any { it.isBlank() })
    }
}
