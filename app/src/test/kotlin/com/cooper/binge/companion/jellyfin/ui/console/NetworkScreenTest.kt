package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.NetworkImpact
import com.cooper.binge.companion.jellyfin.server.networkKeys
import com.cooper.binge.companion.jellyfin.server.networkSettings
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** What the networking page shows for a release, and what it never shows. */
@RunWith(RobolectricTestRunner::class)
class NetworkScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val fixture = FakeJellyfin().also { it.close() }.networkConfiguration

    private fun show(
        version: String,
        json: String = fixture,
        draft: Set<String> = emptySet(),
        actions: NetworkActions = NetworkActions(),
        confirming: Set<NetworkImpact>? = null,
    ) {
        val keys = networkKeys(JellyfinServerProfile.from(version))
        val state =
            NetworkUiState.Ready(
                Json.parseToJsonElement(json).jsonObject.networkSettings(keys),
                keys,
                draft,
                confirming = confirming,
            )
        compose.setContent { JellyfinTheme { NetworkScreen(state, actions, appUrl = "http://nas:8096") } }
    }

    @Test
    fun `the certificate password is never shown, though the server sent one`() {
        show("10.10.7")
        compose.onNodeWithText("Certificate file").performScrollTo().assertExists()
        compose.onAllNodesWithText("s3cret", substring = true).assertCountEquals(0)
    }

    @Test
    fun `UPnP shows before 10_11 and not from it, though the server still sends it`() {
        show("10.10.7")
        compose.onNodeWithText("UPnP port mapping").performScrollTo().assertExists()
    }

    @Test
    fun `UPnP is gone from 10_11`() {
        show("10.11.6")
        compose.onAllNodesWithText("UPnP port mapping").assertCountEquals(0)
        compose.onNodeWithText("Auto discovery").performScrollTo().assertExists()
    }

    @Test
    fun `a row shows only if the server sent its option`() {
        show("10.10.7", json = """{"InternalHttpPort":8096,"PublicHttpPort":8096}""")
        compose.onNodeWithText("HTTP port").assertExists()
        compose.onAllNodesWithText("HTTPS port").assertCountEquals(0)
        compose.onAllNodesWithText("Known proxies").assertCountEquals(0)
        compose.onAllNodesWithText("Require HTTPS").assertCountEquals(0)
    }

    @Test
    fun `Save changes waits for a change, and a change not yet saved says so`() {
        show("10.10.7")
        compose.onNodeWithText("Save changes").assertIsNotEnabled()
        compose.onAllNodesWithText("Not saved yet").assertCountEquals(0)
    }

    @Test
    fun `a change not yet saved is marked, and Save changes asks for it`() {
        var saved = 0
        show("10.10.7", draft = setOf("PublicHttpPort"), actions = NetworkActions(onSave = { saved++ }))
        compose.onNodeWithText("Not saved yet").performScrollTo().assertExists()
        compose.onNodeWithText("Save changes").assertIsEnabled().performClick()
        assertEquals(1, saved)
    }

    @Test
    fun `the confirm names how the change reaches this app, and opens on Cancel`() {
        var confirmed = 0
        show(
            "10.10.7",
            confirming = setOf(NetworkImpact.Port, NetworkImpact.BindAddress),
            actions = NetworkActions(onConfirm = { confirmed++ }),
        )
        compose.onNodeWithText("Save these changes?").assertExists()
        compose.onNodeWithText("This app connects on the port you are changing.", substring = true).assertExists()
        compose.onNodeWithText("unable to connect to the server until you change it back", substring = true).assertExists()
        compose.onNodeWithText("http://nas:8096", substring = true).assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        assertEquals(0, confirmed)
    }

    @Test
    fun `a restart is said to be needed after a save, and opens the Server page`() {
        var opened = 0
        val keys = networkKeys(JellyfinServerProfile.from("10.10.7"))
        val state = NetworkUiState.Ready(Json.parseToJsonElement(fixture).jsonObject.networkSettings(keys), keys, restartNeeded = true)
        compose.setContent { JellyfinTheme { NetworkScreen(state, NetworkActions(onOpenServer = { opened++ })) } }
        // Clicked by its action: the footer covers the last row in a window this small.
        compose.onNodeWithText("Restart to apply").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, opened)
    }

    @Test
    fun `the password row says only whether a new one is waiting, never a value`() {
        show("10.10.7", draft = setOf("CertificatePassword"))
        compose.onNodeWithText("Certificate password").performScrollTo().assertExists()
        compose.onNodeWithText("A new password, not saved yet").performScrollTo().assertExists()
        compose.onAllNodesWithText("s3cret", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the password row says it stays as it is when nothing is waiting`() {
        show("10.10.7")
        compose.onNodeWithText("Stays as it is").performScrollTo().assertExists()
    }

    @Test
    fun `every row is disabled while Save changes is writing`() {
        val keys = networkKeys(JellyfinServerProfile.from("10.10.7"))
        val state =
            NetworkUiState.Ready(
                Json.parseToJsonElement(fixture).jsonObject.networkSettings(keys),
                keys,
                setOf("PublicHttpPort"),
                committing = true,
            )
        compose.setContent { JellyfinTheme { NetworkScreen(state, NetworkActions()) } }
        compose.onNodeWithText("Auto discovery").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Known proxies").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("HTTP port").performScrollTo().assertIsNotEnabled()
    }
}
