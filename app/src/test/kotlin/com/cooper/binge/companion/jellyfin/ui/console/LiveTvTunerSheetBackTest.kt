package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.LiveTvSummary
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_M3U
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * System Back steps back through the tuner sheets to the one a sheet was opened from: a form to the choice or to what the search
 * found, the search to the choice. The choice, and a tuner opened from the page's own row, close the sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LiveTvTunerSheetBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val shown = mutableListOf<TunerSheet?>()
    private val device = TunerDraft(TUNER_TYPE_HDHOMERUN, name = "HDHomeRun Prime", url = "http://192.168.1.61")

    private fun backFrom(
        sheet: TunerSheet,
        saving: Boolean = false,
    ): List<TunerSheet?> {
        compose.setContent {
            JellyfinTheme {
                TunerSheets(
                    LiveTvUiState.Ready(LiveTvSummary(), sheet = sheet, saving = saving),
                    LiveTvActions(onSheet = { shown += it }),
                )
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        return shown.toList()
    }

    @Test
    fun `Back from a form opened from the choice returns to the choice`() =
        assertEquals(
            listOf<TunerSheet?>(TunerSheet.Choose),
            backFrom(TunerSheet.Form(TunerDraft(TUNER_TYPE_M3U), from = TunerSheet.Choose)),
        )

    @Test
    fun `Back from a form opened from the search results returns to the results`() {
        val found = TunerSheet.Found(listOf(device))
        assertEquals(listOf<TunerSheet?>(found), backFrom(TunerSheet.Form(device, from = found)))
    }

    @Test
    fun `Back from the search, in any state, returns to the choice`() {
        assertEquals(listOf<TunerSheet?>(TunerSheet.Choose), backFrom(TunerSheet.Found(listOf(device))))
    }

    @Test
    fun `Back from the choice closes the sheet`() {
        val result = backFrom(TunerSheet.Choose)
        assertEquals(1, result.size)
        assertNull(result.single())
    }

    @Test
    fun `Back from a tuner opened from the page's row closes the sheet`() {
        val result = backFrom(TunerSheet.Form(device.copy(id = "h1")))
        assertEquals(listOf<TunerSheet?>(null), result)
    }

    @Test
    fun `Back while a tuner is being saved closes the sheet rather than stepping back`() =
        assertEquals(listOf<TunerSheet?>(null), backFrom(TunerSheet.Form(device, from = TunerSheet.Choose), saving = true))

    @Test
    fun `each sheet knows the one before it`() {
        assertNull(TunerSheet.Choose.previous())
        assertEquals(TunerSheet.Choose, TunerSheet.Discovering.previous())
        assertEquals(TunerSheet.Choose, TunerSheet.Found(emptyList()).previous())
        assertNull(TunerSheet.Form(device).previous())
    }
}
