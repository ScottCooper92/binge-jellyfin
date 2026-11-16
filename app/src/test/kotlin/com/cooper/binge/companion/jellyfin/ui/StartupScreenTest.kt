package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.assertNoTextClipped
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.Country
import com.cooper.binge.companion.jellyfin.server.Culture
import com.cooper.binge.companion.jellyfin.server.PublicServerInfo
import com.cooper.binge.companion.jellyfin.server.StartupConfiguration
import com.cooper.binge.companion.jellyfin.server.UiLanguage
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.console.ConsoleFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The setup wizard's screens, drawn from state: what each phase shows and what its controls ask for. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class StartupScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val server =
        PublicServerInfo("Living room", "10.11.6", "a1", "http://192.168.1.20:8096".toHttpUrl(), startupWizardCompleted = false)

    private fun configuration(withName: Boolean = true) =
        StartupConfiguration(
            Json
                .parseToJsonElement(
                    (if (withName) """{"ServerName":"Living room",""" else "{") +
                        """"UICulture":"en-GB","MetadataCountryCode":"GB","PreferredMetadataLanguage":"en"}""",
                ).jsonObject,
        )

    private fun configuring(
        withName: Boolean = true,
        busy: Boolean = false,
        error: SetupError? = null,
    ) = StartupPhase.Configuring(
        configuration = configuration(withName),
        uiLanguages = listOf(UiLanguage("English (United Kingdom)", "en-GB"), UiLanguage("Español", "es")),
        cultures = listOf(Culture("English", "en", listOf("eng")), Culture("Spanish", "es", listOf("spa"))),
        countries = listOf(Country("GB", "United Kingdom"), Country("ES", "Spain")),
        busy = busy,
        error = error,
    )

    private fun show(
        phase: StartupPhase,
        actions: StartupActions = StartupActions(),
    ) = compose.setContent {
        JellyfinTheme { JellyfinRoot(UiState.Startup("http://192.168.1.20:8096", server, phase), Actions(startup = actions)) }
    }

    @Test
    fun `the first step shows the name and the three choices as they are held, and Save asks to save`() {
        val calls = mutableListOf<String>()
        show(configuring(), StartupActions(onSave = { calls += "save" }))
        compose.onNodeWithText("Set up your server").assertIsDisplayed()
        compose.onNodeWithText("Living room").assertIsDisplayed()
        compose.onNodeWithText("English (United Kingdom)").assertIsDisplayed()
        compose.onNodeWithText("United Kingdom").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun `a server that sent no name gets no name field`() {
        show(configuring(withName = false))
        compose.onNodeWithText("Server name").assertDoesNotExist()
        compose.onNodeWithText("Metadata country").assertIsDisplayed()
    }

    @Test
    fun `a language is chosen in a sheet with a filter, and the pick reaches the wizard`() {
        val picked = mutableListOf<String>()
        show(configuring(), StartupActions(onMetadataCountry = { picked += it }))
        compose.onNodeWithText("Metadata country").performClick()
        // The name field and the sheet's filter.
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onNodeWithText("Spain").performClick()
        assertEquals(listOf("ES"), picked)
    }

    @Test
    fun `a refused save says so beside Save`() {
        show(configuring(error = SetupError.Generic))
        compose.onNodeWithText("Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a failed read is an error screen with Try again`() {
        val calls = mutableListOf<String>()
        show(StartupPhase.Failed(ConsoleFailure.Unreachable), StartupActions(onRetry = { calls += "retry" }))
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `the account step shows the fields, and Create account asks to send them once they agree`() {
        val calls = mutableListOf<String>()
        show(StartupPhase.Account("sam", "hunter2", "hunter2"), StartupActions(onSaveAccount = { calls += "save" }))
        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithText("Password again").assertIsDisplayed()
        compose.onNodeWithText("Create account").performClick()
        assertEquals(listOf("save"), calls)
    }

    @Test
    fun `two passwords that differ say so, and Create account is off`() {
        show(StartupPhase.Account("sam", "hunter2", "hunter"))
        compose.onNodeWithText("The passwords don’t match.").assertIsDisplayed()
        compose.onNodeWithText("Create account").assertIsNotEnabled()
    }

    @Test
    fun `an empty password turns Create account off`() {
        show(StartupPhase.Account("sam", "", ""))
        compose.onNodeWithText("Create account").assertIsNotEnabled()
        compose.onNodeWithText("Optional. You can set one later.").assertDoesNotExist()
    }

    @Test
    fun `the server's own words for a refusal show in place of the generic sentence`() {
        show(StartupPhase.Account("sam", "pw", "pw", error = SetupError.Generic, reason = "Password must not be empty"))
        compose.onNodeWithText("Password must not be empty").assertIsDisplayed()
        compose.onNodeWithText("Something went wrong. Try again.").assertDoesNotExist()
    }

    @Test
    fun `a blank name turns Create account off`() {
        show(StartupPhase.Account("", "", ""))
        compose.onNodeWithText("Create account").assertIsNotEnabled()
    }

    @Test
    fun `the last step shows the two choices, and Finish setup asks to finish`() {
        val calls = mutableListOf<String>()
        show(StartupPhase.Finish("sam", "pw", remoteAccess = true), StartupActions(onFinish = { calls += "finish" }))
        compose.onNodeWithText("Remote access").assertIsDisplayed()
        compose.onNodeWithText("Allow remote connections").assertIsDisplayed()
        compose.onNodeWithText("Automatic port mapping").assertIsDisplayed()
        compose.onNodeWithText("Finish setup").performClick()
        assertEquals(listOf("finish"), calls)
    }

    @Test
    fun `port mapping is offered only while remote access is on`() {
        show(StartupPhase.Finish("sam", "pw", remoteAccess = false))
        compose.onNodeWithText("Automatic port mapping").assertDoesNotExist()
    }

    @Test
    fun `a switch asks the wizard to change it`() {
        val calls = mutableListOf<Boolean>()
        show(StartupPhase.Finish("sam", "pw", remoteAccess = true), StartupActions(onRemoteAccess = { calls += it }))
        compose.onNodeWithText("Allow remote connections").performClick()
        assertEquals(listOf(false), calls)
    }

    @Test
    fun `the notice offers to set the server up`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            JellyfinTheme {
                JellyfinRoot(
                    UiState.SignIn("http://192.168.1.20:8096", server),
                    Actions(startup = StartupActions(onOpen = { calls += "open" })),
                )
            }
        }
        compose.onNodeWithText("Set up this server").performClick()
        assertEquals(listOf("open"), calls)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the font nothing on the first step is cropped`() {
        show(configuring(error = SetupError.Generic))
        assertTrue(compose.assertNoTextClipped() > 0)
    }
}
