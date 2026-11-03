package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.jellyfin.server.SdCountry
import com.cooper.binge.companion.jellyfin.server.SdLineup
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class LiveTvSchedulesDirectSheetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val usa = SdCountry("USA", "United States", "North America")
    private val gbr = SdCountry("GBR", "United Kingdom", "Europe")

    private fun step(
        step: SdStep,
        actions: SdActions = SdActions(),
        saving: Boolean = false,
    ) = compose.setContent { JellyfinTheme { SdStepContent(step, saving, actions) } }

    @Test
    fun `the account step sends what was typed, and says the password is not kept`() {
        val sent = mutableListOf<Pair<String, String>>()
        step(SdStep.Account, SdActions(onSignIn = { user, password -> sent += user to password }))
        compose.onNodeWithText("does not keep it", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Username").performTextInput("carol")
        compose.onNodeWithText("Password").performTextInput("hunter2")
        compose.onNodeWithText("Next").performClick()
        assertEquals(listOf("carol" to "hunter2"), sent)
    }

    @Test
    fun `the location step says the account is saved, and opens the country list`() {
        var opened = false
        step(SdStep.Location("g2", listOf(usa, gbr)), SdActions(onChooseCountry = { opened = it }))
        compose.onNodeWithText("The account is saved", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Choose a country").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `countries still being read are not offered, and an empty list says so`() {
        step(SdStep.Location("g2", countries = null))
        compose.onNodeWithText("Loading countries").assertIsDisplayed()
    }

    @Test
    fun `the country list picks one`() {
        val picked = mutableListOf<SdCountry>()
        step(SdStep.Location("g2", listOf(usa, gbr), choosing = true), SdActions(onPickCountry = { picked += it }))
        compose.onNodeWithText("United Kingdom").performClick()
        assertEquals(listOf(gbr), picked)
    }

    @Test
    fun `a typed postal code is reported, and Find sends the one the step holds`() {
        val typed = mutableListOf<String>()
        val found = mutableListOf<String>()
        step(
            SdStep.Location("g2", listOf(usa), country = usa, postalCode = "10001"),
            SdActions(onPostalCode = { typed += it }, onFind = { found += it }),
        )
        compose.onNodeWithText("Postal code").performTextInput("2")
        compose.onNodeWithText("Find lineups").performClick()
        assertEquals(1, typed.size)
        assertEquals(true, typed.single().contains("10001"))
        assertEquals(listOf("10001"), found)
    }

    @Test
    fun `Find lineups is disabled until a country and a postal code are set`() {
        step(SdStep.Location("g2", listOf(usa), country = usa, postalCode = ""))
        compose.onNodeWithText("Find lineups").assertIsNotEnabled()
    }

    @Test
    fun `a lineup is picked from the list`() {
        val added = mutableListOf<SdLineup>()
        val comcast = SdLineup("USA-NY12345-X", "Comcast Digital")
        step(SdStep.Lineups("g2", listOf(usa), usa, "10001", listOf(comcast)), SdActions(onAddLineup = { added += it }))
        compose.onNodeWithText("Comcast Digital").performClick()
        assertEquals(listOf(comcast), added)
    }

    @Test
    fun `no lineups says the login may be the reason, as the server does not tell them apart`() {
        step(SdStep.Lineups("g2", listOf(usa), usa, "10001", emptyList()))
        compose.onNodeWithText("when the login fails", substring = true).assertIsDisplayed()
    }

    @Test
    fun `back returns from the lineups to the location`() {
        var back = 0
        step(SdStep.Lineups("g2", listOf(usa), usa, "10001", emptyList()), SdActions(onBackToLocation = { back++ }))
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, back)
    }
}
