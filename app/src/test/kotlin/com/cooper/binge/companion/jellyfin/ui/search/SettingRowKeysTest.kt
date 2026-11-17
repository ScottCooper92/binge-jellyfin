package com.cooper.binge.companion.jellyfin.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.binge.designsystem.component.ProvideRowFocus
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.R
import com.cooper.binge.companion.jellyfin.server.AdvancedSettings
import com.cooper.binge.companion.jellyfin.server.Branding
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.LogFile
import com.cooper.binge.companion.jellyfin.server.PlaybackPreferences
import com.cooper.binge.companion.jellyfin.server.ServerSettings
import com.cooper.binge.companion.jellyfin.server.SubtitleMode
import com.cooper.binge.companion.jellyfin.server.networkKeys
import com.cooper.binge.companion.jellyfin.server.networkSettings
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.Actions
import com.cooper.binge.companion.jellyfin.ui.AppSettings
import com.cooper.binge.companion.jellyfin.ui.NotificationSettings
import com.cooper.binge.companion.jellyfin.ui.ThisAppScreen
import com.cooper.binge.companion.jellyfin.ui.console.AdvancedActions
import com.cooper.binge.companion.jellyfin.ui.console.AdvancedScreen
import com.cooper.binge.companion.jellyfin.ui.console.AdvancedUiState
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.binge.companion.jellyfin.ui.console.HomeLayoutActions
import com.cooper.binge.companion.jellyfin.ui.console.HomeLayoutScreen
import com.cooper.binge.companion.jellyfin.ui.console.HomeLayoutUiState
import com.cooper.binge.companion.jellyfin.ui.console.LogsActions
import com.cooper.binge.companion.jellyfin.ui.console.LogsScreen
import com.cooper.binge.companion.jellyfin.ui.console.LogsUiState
import com.cooper.binge.companion.jellyfin.ui.console.NetworkActions
import com.cooper.binge.companion.jellyfin.ui.console.NetworkScreen
import com.cooper.binge.companion.jellyfin.ui.console.NetworkUiState
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackActions
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackScreen
import com.cooper.binge.companion.jellyfin.ui.console.PlaybackUiState
import com.cooper.binge.companion.jellyfin.ui.console.ServerSettingsActions
import com.cooper.binge.companion.jellyfin.ui.console.ServerSettingsScreen
import com.cooper.binge.companion.jellyfin.ui.console.ServerSettingsUiState
import com.cooper.binge.companion.jellyfin.ui.console.TranscodingActions
import com.cooper.binge.companion.jellyfin.ui.console.TranscodingScreen
import com.cooper.binge.companion.jellyfin.ui.console.TranscodingUiState
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.uiandroid.resolve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The settings search opens a page on a row when an entry's `row` is the `ListItem.key` the page gives it, and the two halves are
 * written in different files. A page that drops or renames a key leaves its entry opening the page at the top once the focus gives
 * up, and nothing goes red. This renders each page that has a keyed entry, from fixtures, on a window too short to show any row
 * without a scroll, and asks the same `ProvideRowFocus` the console routes use to open it on the entry's row: the row is on screen
 * afterwards only if the page carried the key (the registration itself is internal to the design system).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class SettingRowKeysTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val profile = JellyfinServerProfile.from("10.11.6")
    private val admin = JellyfinAccount("sam", buildJsonObject { put("IsAdministrator", true) })

    private val fixtures = FakeJellyfin().also { it.close() }

    /** What each page shows, from fixtures that have every row the entries name. */
    private val pages: Map<ConsolePage, @Composable () -> Unit> =
        mapOf(
            ConsolePage.ServerSettings to {
                ServerSettingsScreen(
                    ServerSettingsUiState.Ready(
                        settings = ServerSettings("truenas", "en", "GB", quickConnect = true),
                        branding = Branding(),
                        cultures = emptyList(),
                        countries = emptyList(),
                    ),
                    ServerSettingsActions(),
                )
            },
            ConsolePage.Transcoding to {
                val encoding = Json.parseToJsonElement(fixtures.encodingConfiguration).jsonObject
                val server = Json.parseToJsonElement("""{"MinResumePct":5,"RemoteClientBitrateLimit":8000000}""").jsonObject
                TranscodingScreen(TranscodingUiState.Ready(encoding, server), TranscodingActions(), profile)
            },
            ConsolePage.Network to {
                val keys = networkKeys(profile)
                val settings = Json.parseToJsonElement(fixtures.networkConfiguration).jsonObject.networkSettings(keys)
                NetworkScreen(NetworkUiState.Ready(settings, keys, emptySet()), NetworkActions(), appUrl = "http://nas:8096")
            },
            ConsolePage.Advanced to {
                AdvancedScreen(AdvancedUiState.Ready(AdvancedSettings(present = setOf("CacheSize"), cacheSize = 800)), AdvancedActions())
            },
            ConsolePage.Logs to {
                LogsScreen(
                    LogsUiState.Ready(listOf(LogFile("log_20261031.log", 412_000, 1_793_534_400_000L)), retention = 30),
                    LogsActions(),
                )
            },
            ConsolePage.Playback to {
                PlaybackScreen(PlaybackUiState.Ready(PlaybackPreferences("en", "en", SubtitleMode.Default), emptyList()), PlaybackActions())
            },
            ConsolePage.HomeLayout to {
                HomeLayoutScreen(
                    HomeLayoutUiState.Ready(views = emptyList(), layout = HomeLayout()),
                    HomeLayoutActions(),
                    openLibrary = null,
                )
            },
            ConsolePage.ThisApp to {
                ThisAppScreen(Actions(appSettings = AppSettings(), notifications = NotificationSettings()), onBack = {})
            },
        )

    /** The text of a row where it is not the entry's own words: the entry names the setting, the row may carry its own label. */
    private val rowLabels = mapOf(SettingRows.TRANSCODING_ACCELERATION to R.string.transcoding_acceleration)

    private val keyed =
        settingsIndex(
            RuntimeEnvironment.getApplication().resources,
        ).filter { it.row != null && it.available(profile, admin) }

    private fun rowLabel(entry: SettingEntry<SearchTarget>) =
        rowLabels[entry.row]?.let(compose.activity::getString) ?: entry.label.resolve(compose.activity)

    private var page by mutableStateOf<ConsolePage?>(null)
    private var focusRow by mutableStateOf<String?>(null)

    /** Each try composes its page afresh, so what an earlier one scrolled to is not left behind. */
    private var attempt by mutableStateOf(0)

    private fun show() =
        compose.setContent {
            JellyfinTheme {
                val current = page
                if (current != null) {
                    // A new page starts clean, as the console route's own composition does when it opens a page.
                    key(current, attempt) {
                        // Too short to show any row without a scroll: what is outside it is not on screen.
                        Box(Modifier.fillMaxWidth().height(PAGE_HEIGHT).clipToBounds()) {
                            ProvideRowFocus(key = focusRow) { pages.getValue(current)() }
                        }
                    }
                }
            }
        }

    private fun isShown(label: String): Boolean {
        val nodes = compose.onAllNodesWithText(label)
        return nodes.fetchSemanticsNodes().indices.any { nodes[it].isDisplayed() }
    }

    private fun fresh(on: ConsolePage) {
        focusRow = null
        page = on
        attempt++
        compose.waitForIdle()
    }

    /** Opens [on] afresh and asks for [row]; whether [label] is on screen once the focus has had its time. */
    private fun reached(
        on: ConsolePage,
        row: String?,
        label: String,
    ): Boolean {
        fresh(on)
        focusRow = row
        return waitFor(label)
    }

    private fun waitFor(label: String): Boolean {
        repeat(WAIT_STEPS) {
            compose.mainClock.advanceTimeBy(STEP_MILLIS)
            compose.waitForIdle()
            if (isShown(label)) return true
        }
        return false
    }

    @Test
    fun `every page that has a keyed entry is covered here`() {
        assertEquals(emptySet<ConsolePage>(), keyed.map { it.target.page }.toSet() - pages.keys)
    }

    @Test
    fun `each keyed entry opens its page on a row that is on screen`() {
        show()
        val missed = mutableListOf<String>()
        keyed.forEach { entry ->
            val label = rowLabel(entry)
            // The window is too short to show the row before the focus scrolls to it: a row that is there already proves nothing.
            fresh(entry.target.page)
            if (isShown(label)) missed += "${entry.row}: '$label' is on screen without a scroll, so the window proves nothing"
            focusRow = entry.row
            if (!waitFor(label)) missed += "${entry.row}: '$label' never came on screen on ${entry.target.page}"
        }
        assertEquals(emptyList<String>(), missed)
    }

    @Test
    fun `a key no page carries is not found, so the check above can fail`() {
        show()
        val entry = keyed.first()
        val label = rowLabel(entry)
        assertFalse(reached(entry.target.page, "no.such.row", label))
        assertTrue(reached(entry.target.page, entry.row, label))
    }

    @Test
    @Config(qualifiers = "w411dp-h2000dp", fontScale = 2f)
    fun `at twice the font the notifications row below a long page is still found`() {
        show()
        val entry = keyed.first { it.id.endsWith(":notifications_switch") }
        val label = rowLabel(entry)
        assertTrue(reached(entry.target.page, entry.row, label))
    }

    private companion object {
        val PAGE_HEIGHT = 64.dp
        const val WAIT_STEPS = 40
        const val STEP_MILLIS = 250L
    }
}
