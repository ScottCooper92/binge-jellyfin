package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.SdCountry
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * System Back steps back through the Schedules Direct setup: the account returns to the choice, the country list to the
 * location, the lineups to the location. The location has nothing before it, so Back closes the sheet there, as it does while
 * a call is in flight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class SdSheetBackTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val log = mutableListOf<String>()
    private val countries = listOf(SdCountry("GB", "United Kingdom", "Europe"))

    private val actions =
        SdActions(
            onChooseCountry = { log += "choose country $it" },
            onBackToLocation = { log += "back to location" },
            onBackToChoice = { log += "back to choice" },
            onClose = { log += "close" },
        )

    private fun backFrom(
        step: SdStep,
        saving: Boolean = false,
    ): List<String> {
        compose.setContent { JellyfinTheme { SdSheet(SdUiState(step = step, saving = saving), actions) } }
        compose.waitForIdle()
        compose.runOnUiThread { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.waitForIdle()
        return log.toList()
    }

    @Test
    fun `Back from the account returns to the choice`() = assertEquals(listOf("back to choice"), backFrom(SdStep.Account))

    @Test
    fun `Back from the country list returns to the location`() =
        assertEquals(listOf("choose country false"), backFrom(SdStep.Location("p1", countries, choosing = true)))

    @Test
    fun `Back from the lineups returns to the location`() =
        assertEquals(listOf("back to location"), backFrom(SdStep.Lineups("p1", countries, countries.first(), "SW1A", lineups = null)))

    @Test
    fun `Back from the location closes the sheet, the account being saved already`() =
        assertEquals(listOf("close"), backFrom(SdStep.Location("p1", countries)))

    @Test
    fun `Back while the account is being saved closes the sheet rather than stepping back`() =
        assertEquals(listOf("close"), backFrom(SdStep.Account, saving = true))
}
