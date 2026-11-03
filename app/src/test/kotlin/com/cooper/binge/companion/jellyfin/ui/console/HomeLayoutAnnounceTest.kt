package com.cooper.binge.companion.jellyfin.ui.console

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.server.HomeLayout
import com.cooper.binge.companion.jellyfin.server.UserView
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** A library's move on its Home screen page is announced, as TalkBack, once it has been made. */
@RunWith(RobolectricTestRunner::class)
class HomeLayoutAnnounceTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val views = listOf(UserView("a", "Movies", "movies"), UserView("b", "Shows", "tvshows"), UserView("c", "Music", "music"))
    private var state by mutableStateOf(HomeLayoutUiState.Ready(views, HomeLayout(), openId = "b"))

    private fun announcements(): List<String> {
        val manager = compose.activity.getSystemService(AccessibilityManager::class.java)
        return shadowOf(manager)
            .sentAccessibilityEvents
            .filter { it.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT }
            .map { it.text.joinToString() }
    }

    @Test
    fun `a move says the new position, and opening the page says nothing`() {
        val manager = compose.activity.getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).setEnabled(true)
        compose.setContent { JellyfinTheme { HomeLayoutScreen(state, HomeLayoutActions()) } }
        compose.waitForIdle()
        assertTrue(announcements().isEmpty())
        state = state.copy(layout = HomeLayout(order = listOf("b", "a", "c")))
        compose.waitForIdle()
        assertEquals(listOf("Moved to position 1 of 3"), announcements())
    }
}
