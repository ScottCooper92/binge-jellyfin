package com.cooper.binge.companion.jellyfin.ui.search

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.console.ConsolePage
import com.cooper.homelab.core.foundation.UiText
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.presentation.search.SettingsSearchState
import com.cooper.homelab.core.uiandroid.resolve
import com.cooper.homelab.core.uiandroid.search.SettingsSearchScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class SettingsSearchScreenTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private var opened: ConsolePage? = null

    private val profile = JellyfinServerProfile.from("10.11.6")

    private fun accountOf(admin: Boolean) = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":$admin}""").jsonObject)

    private fun page(
        account: JellyfinAccount,
        recent: (List<SettingEntry<SearchTarget>>) -> List<SettingEntry<SearchTarget>> = { emptyList() },
        onClearRecent: () -> Unit = {},
    ) = compose.setContent {
        JellyfinTheme {
            val entries = settingsIndex(LocalContext.current.resources)
            SettingsSearchScreen(
                state = SettingsSearchState.Ready({ it.available(profile, account) }, recent(entries), Unit),
                entries = entries,
                icon = { it.target.icon },
                onOpen = { entry, _ -> opened = entry.target.page },
                onBack = {},
                onRetry = {},
                onClearRecent = onClearRecent,
            )
        }
    }

    @Test
    fun `before a query it says what to type`() {
        page(accountOf(admin = true))
        compose.onNodeWithText("Type the name of a setting, or what it does.").assertExists()
    }

    @Test
    fun `a result says where the setting is, and opens its page`() {
        page(accountOf(admin = true))
        compose.onNode(hasSetTextAction()).performTextInput("https port")
        compose.onAllNodesWithText("Advanced › Networking").onFirst().assertExists()
        compose.onNodeWithText("HTTPS port").performClick()
        assertEquals(ConsolePage.Network, opened)
    }

    @Test
    fun `a user is not offered what only an administrator may open`() {
        page(accountOf(admin = false))
        compose.onNode(hasSetTextAction()).performTextInput("hardware")
        compose.onAllNodesWithText("Hardware acceleration").assertCountEquals(0)
        compose.onNodeWithText("No settings found").assertExists()
    }

    @Test
    fun `before a query it lists what was opened last, and Clear asks to forget them`() {
        var cleared = false
        page(
            accountOf(admin = true),
            recent = { all -> all.filter { it.target.page == ConsolePage.Transcoding && it.pageLabel == null } },
            onClearRecent = { cleared = true },
        )
        compose.onNodeWithText("Recent search results").assertExists()
        compose.onNodeWithText("Type the name of a setting, or what it does.").assertDoesNotExist()
        compose.onNodeWithText("Playback and transcoding").performClick()
        assertEquals(ConsolePage.Transcoding, opened)
        compose.onNodeWithText("Clear").performClick()
        assertTrue(cleared)
    }

    @Test
    fun `an entry's id names its page and its label, not a number`() {
        val entry = settingsIndex(compose.activity.resources).first { it.target.page == ConsolePage.Transcoding && it.pageLabel == null }
        assertEquals("Transcoding:transcoding_title", entry.id)
    }

    /** The key of every text in the index names a string, in the default language and in Spanish, so the shared screen never throws on one. */
    @Test
    fun `every text the index names is a string in both languages`() {
        val keys = settingsIndex(compose.activity.resources).flatMap { listOfNotNull(it.label, it.section, it.pageLabel, it.keywords) }
        listOf(Locale.ENGLISH, Locale("es")).forEach { locale ->
            val config = Configuration(compose.activity.resources.configuration).apply { setLocale(locale) }
            val context = compose.activity.createConfigurationContext(config)
            keys.forEach { assertTrue("${(it as UiText.Keyed).key.id} in $locale", it.resolve(context).isNotBlank()) }
        }
    }
}
