package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.JellyfinServerProfile
import com.cooper.binge.companion.jellyfin.server.JellyfinVersion
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A library's route resets what its screen left behind when the library opens, but only once: the Activity being
 * recreated (a rotation, a fold) must keep a sheet that is open (#395, #392).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class LibraryRouteRecreationTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val profile = JellyfinServerProfile(JellyfinVersion(10, 11, 0))

    @Before
    fun signIn() {
        runBlocking { sessions.save(jellyfin.session()) }
    }

    @After
    fun stop() = jellyfin.close()

    private fun show() =
        compose.setContent {
            JellyfinTheme { LibraryItemRoute(connection, profile, libraryItem("lib-movies"), openSubItem = {}, onBack = {}) }
        }

    @Test
    fun `an open rename sheet survives the Activity being recreated`() {
        show()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("Name")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Name").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("Rename library")).fetchSemanticsNodes().isNotEmpty() }

        compose.scenario.recreate()
        show()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("Rename library")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Rename library").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
