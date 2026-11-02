package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.ApiKey
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinHttpException
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.ui.ViewModelTeardown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** The server's own words for a refusal reach the screens, and nothing else does. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConsoleFailureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a refusal carries the server's reason, and an unreachable server has none`() {
        val refused = JellyfinHttpException(409, "Backups", "The backup directory has less than 5 GiB free.").consoleFailure()
        assertEquals("The backup directory has less than 5 GiB free.", refused.reason)
        assertEquals(ConsoleFailure.Refused, refused)
        assertNull(IOException("no route").consoleFailure().reason)
        assertEquals(ConsoleFailure.Unreachable, IOException("no route").consoleFailure())
        assertNull(JellyfinHttpException(500, "x").consoleFailure().reason)
        assertNull(JellyfinHttpException(500, "x", "   ").consoleFailure().reason)
    }

    @Test
    fun `a 401 keeps its own sentence, not the server's text`() {
        assertNull(JellyfinHttpException(401, "x", "Invalid token").consoleFailure().reason)
    }

    @Test
    fun `two refusals are the same failure whatever their reason, and a refusal is not an unreachable server`() {
        assertEquals(ConsoleFailure.refused("a"), ConsoleFailure.refused("b"))
        assertEquals(ConsoleFailure.Refused, ConsoleFailure.refused(null))
        assertNotEquals(ConsoleFailure.Unreachable, ConsoleFailure.refused("a"))
    }

    private val keys = listOf(ApiKey("0f3a9c2e", "Sonarr", null))

    @Test
    fun `an action page says the server's reason in its message, without the generic line`() {
        compose.setContent {
            JellyfinTheme {
                ApiKeysScreen(
                    ApiKeysUiState.Ready(keys, actionFailed = ConsoleFailure.refused("Not enough free space.")),
                    ApiKeysActions(),
                )
            }
        }
        compose.onNodeWithText("Not changed. Not enough free space.").assertExists()
        compose.onAllNodesWithText("Something went wrong", substring = true).assertCountEquals(0)
    }

    @Test
    fun `without a reason the page keeps the generic line`() {
        compose.setContent {
            JellyfinTheme { ApiKeysScreen(ApiKeysUiState.Ready(keys, actionFailed = ConsoleFailure.Refused), ApiKeysActions()) }
        }
        compose.onNodeWithText("Not changed. Something went wrong. Try again.").assertExists()
    }

    @Test
    fun `a page that couldn't read shows the reason in place of the generic body, with Try again`() {
        compose.setContent {
            JellyfinTheme {
                ApiKeysScreen(ApiKeysUiState.Failed(ConsoleFailure.refused("The database is locked.")), ApiKeysActions())
            }
        }
        compose.onNodeWithText("The database is locked.").assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun `the reason is read from the server's error answer through a real call`() {
        val jellyfin = FakeJellyfin()
        val teardown = ViewModelTeardown()
        try {
            Dispatchers.setMain(Dispatchers.Unconfined)
            val sessions = testSessionStore()
            runBlocking { sessions.save(jellyfin.session()) }
            val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
            jellyfin.status["/System/Configuration"] = 500
            jellyfin.statusBody["/System/Configuration"] = """{"title":"The disk is full."}"""
            val vm = teardown.made(ServerSettingsViewModel(connection))
            runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it !is ServerSettingsUiState.Loading } } }
            val failure = runBlocking { withTimeout(TIMEOUT_MILLIS) { vm.state.first { it is ServerSettingsUiState.Failed } } }
            assertEquals("The disk is full.", (failure as ServerSettingsUiState.Failed).failure.reason)
        } finally {
            teardown.close(jellyfin)
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
