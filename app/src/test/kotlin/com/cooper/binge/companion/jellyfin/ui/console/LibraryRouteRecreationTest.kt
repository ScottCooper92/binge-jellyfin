package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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

    private var item by mutableStateOf(libraryItem("lib-movies"))

    private fun show() =
        compose.setContent {
            JellyfinTheme { LibraryItemRoute(connection, profile, item, openSubItem = { item = it }, onBack = {}) }
        }

    private fun waitFor(text: String) =
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
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

    @Test
    fun `a refusal left on the library is not replayed on the page it opens`() {
        show()
        waitFor("Name")
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Enabled"))
        jellyfin.status["/Library/VirtualFolders/LibraryOptions"] = 500
        compose.onNodeWithText("Enabled").performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { compose.onAllNodes(hasText("Not saved", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Download languages"))
        // By action, not by touch: the snackbar the refusal raised sits over the foot of the page.
        compose.onNodeWithText("Download languages").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(TIMEOUT_MILLIS) { item.endsWith("/languages") }
        compose.waitForIdle()
        compose.onAllNodes(hasText("Not saved", substring = true)).assertCountEquals(0)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
