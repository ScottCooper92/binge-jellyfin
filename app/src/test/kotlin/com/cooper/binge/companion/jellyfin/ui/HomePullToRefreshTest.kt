package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.auth.JellyfinSession
import com.cooper.binge.companion.jellyfin.server.JellyfinAccount
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A pull on the console home reads the server, and the library, again in place. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomePullToRefreshTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val session =
        JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token")

    private val state =
        UiState.SignedIn(
            session,
            titles = null,
            health = ConnectionHealth.Healthy,
            account = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":true}""").jsonObject),
        )

    @Test
    fun `pulling the home down asks for a refresh once`() {
        var refreshed = 0
        compose.setContent { JellyfinTheme { JellyfinRoot(state, Actions(onRefresh = { refreshed++ })) } }
        compose.waitForIdle()
        assertEquals(0, refreshed)
        compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput {
            swipe(
                Offset(centerX, top + height * 0.3f),
                Offset(
                    centerX,
                    top + height * 0.9f,
                ),
                durationMillis = 800,
            )
        }
        compose.waitForIdle()
        assertEquals(1, refreshed)
    }
}
