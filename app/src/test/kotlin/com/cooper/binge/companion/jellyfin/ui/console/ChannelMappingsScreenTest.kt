package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.testing.assertNoTextClipped
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.ChannelMappingOptions
import com.cooper.binge.companion.jellyfin.server.ProviderChannel
import com.cooper.binge.companion.jellyfin.server.TunerChannel
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class ChannelMappingsScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val options =
        ChannelMappingOptions(
            providerName = "XMLTV",
            providerChannels = listOf(ProviderChannel("p1", "BBC One"), ProviderChannel("p2", "BBC Two"), ProviderChannel("p3", "ITV")),
            tunerChannels =
                listOf(
                    TunerChannel("c1", "101 BBC One HD", "p1"),
                    TunerChannel("c2", "102 BBC Two HD", "p2"),
                    TunerChannel("c3", "103 ITV HD", null),
                    TunerChannel("c4", "104 ITV2", "p1"),
                ),
        )

    private fun show(
        state: ChannelMappingsUiState,
        actions: ChannelMappingsActions = ChannelMappingsActions(),
    ) = compose.setContent { JellyfinTheme { ChannelMappingsScreen(state, actions) } }

    @Test
    fun `each provider channel shows the tuner channel it is paired with, more than one counted, or that it is not matched`() {
        show(ChannelMappingsUiState.Ready(options))
        compose.onNodeWithText("BBC One").assertIsDisplayed()
        compose.onNodeWithText("101 BBC One HD +1").assertIsDisplayed()
        compose.onNodeWithText("102 BBC Two HD").assertIsDisplayed()
        compose.onNodeWithText("Not matched").assertIsDisplayed()
    }

    @Test
    fun `a tap on a provider channel opens it`() {
        var opened: String? = null
        show(ChannelMappingsUiState.Ready(options), ChannelMappingsActions(onSelect = { opened = it }))
        compose.onNodeWithText("ITV").performClick()
        assertEquals("p3", opened)
    }

    @Test
    fun `an open channel lists the tuner channels, ticks the paired one and says where another is paired`() {
        show(ChannelMappingsUiState.Ready(options, selected = "p1"))
        compose.onNodeWithText("Tuner channel for BBC One").assertIsDisplayed()
        compose.onNodeWithText("103 ITV HD").assertIsDisplayed()
        compose.onNodeWithText("Paired with BBC Two").assertIsDisplayed()
    }

    @Test
    fun `choosing a tuner channel hands it to the page and closes the sheet`() {
        val picks = mutableListOf<String>()
        val selections = mutableListOf<String?>()
        show(
            ChannelMappingsUiState.Ready(options, selected = "p3"),
            ChannelMappingsActions(onPick = { picks += it }, onSelect = { selections += it }),
        )
        compose.onNodeWithText("103 ITV HD").performClick()
        assertEquals(listOf("c3"), picks)
        assertEquals(listOf<String?>(null), selections)
    }

    @Test
    fun `typing in the search reaches the page, and a search that matches nothing says so`() {
        val queries = mutableListOf<String>()
        show(ChannelMappingsUiState.Ready(options, selected = "p3", query = "zzz"), ChannelMappingsActions(onSearch = { queries += it }))
        compose.onNodeWithText("No tuner channels match").assertIsDisplayed()
        compose.onNodeWithText("Search tuner channels").performTextInput("a")
        assertTrue(queries.isNotEmpty())
    }

    @Test
    fun `a guide with no channels says so`() {
        show(ChannelMappingsUiState.Ready(options.copy(providerChannels = emptyList())))
        compose.onNodeWithText("This guide data lists no channels").assertIsDisplayed()
    }

    @Test
    fun `a failed read is an error with a way to try again`() {
        var retried = false
        show(ChannelMappingsUiState.Failed(ConsoleFailure.Unreachable), ChannelMappingsActions(onRetry = { retried = true }))
        compose.onNodeWithText("Try again").performClick()
        assertTrue(retried)
    }

    @Test
    @Config(fontScale = 2f)
    fun `at twice the font nothing on the list is cropped`() {
        show(ChannelMappingsUiState.Ready(options))
        assertTrue(compose.assertNoTextClipped() > 0)
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `at 1_3 times the font nothing in the open channel is cropped`() {
        show(ChannelMappingsUiState.Ready(options, selected = "p1"))
        assertTrue(compose.assertNoTextClipped() > 0)
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp")
    fun `from 600 dp wide the tuner channels sit beside the list, with a prompt until a channel is chosen`() {
        show(ChannelMappingsUiState.Ready(options))
        compose.onNodeWithText("Choose a channel to pick its tuner channel.").assertIsDisplayed()
        compose.onNodeWithText("BBC One").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w900dp-h900dp")
    fun `beside the list a pick keeps the pane open and the list stays`() {
        val picks = mutableListOf<String>()
        val selections = mutableListOf<String?>()
        show(
            ChannelMappingsUiState.Ready(options, selected = "p3"),
            ChannelMappingsActions(onPick = { picks += it }, onSelect = { selections += it }),
        )
        compose.onNodeWithText("Tuner channel for ITV").assertIsDisplayed()
        compose.onAllNodesWithText("BBC One")[0].assertIsDisplayed()
        compose.onNodeWithText("103 ITV HD").performClick()
        assertEquals(listOf("c3"), picks)
        assertTrue(selections.isEmpty())
    }
}
