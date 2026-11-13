package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LicencesScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val library = LicenceEntry("okhttp", "OkHttp", "5.0.0", listOf("Apache-2.0"), "https://square.github.io/okhttp/")

    private fun show(
        state: LicencesUiState,
        onOpenWebsite: (String) -> Unit = {},
    ) = compose.setContent { JellyfinTheme { LicencesScreen(state = state, onBack = {}, onOpenWebsite = onOpenWebsite) } }

    @Test
    fun `a library shows its name, version and licence and opens its website`() {
        val opened = mutableListOf<String>()
        show(LicencesUiState.Ready(listOf(library)), opened::add)
        compose.onNodeWithText("OkHttp").assertIsDisplayed().performClick()
        compose.onNodeWithText("5.0.0 · Apache-2.0").assertIsDisplayed()
        assertEquals(listOf("https://square.github.io/okhttp/"), opened)
    }

    @Test
    fun `a library with no website is not a link`() {
        val opened = mutableListOf<String>()
        show(LicencesUiState.Ready(listOf(library.copy(website = null))), opened::add)
        compose.onNodeWithText("OkHttp").performClick()
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `an empty list says so`() {
        show(LicencesUiState.Ready(emptyList()))
        compose.onNodeWithText("There are no licences to list.").assertIsDisplayed()
    }

    @Test
    fun `an unreadable list says so`() {
        show(LicencesUiState.Unreadable)
        compose.onNodeWithText("The licence list couldn't be read.").assertIsDisplayed()
    }
}
