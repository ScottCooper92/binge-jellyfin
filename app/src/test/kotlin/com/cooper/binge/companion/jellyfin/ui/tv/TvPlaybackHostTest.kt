package com.cooper.binge.companion.jellyfin.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.binge.designsystem.testing.createTakeDownKeyboardAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import com.cooper.binge.companion.jellyfin.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TvPlaybackHostTest {
    @get:Rule
    val compose = createTakeDownKeyboardAndroidComposeRule<ComponentActivity>()

    private val jellyfin = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)

    private var state by mutableStateOf<UiState>(UiState.SignedIn(session = jellyfin.session()))
    private var playback: TvPlayback? = null

    @Before
    fun signedIn() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(jellyfin.session()) }
        compose.setContent { playback = rememberTvPlayback(connection, state) }
    }

    @After
    fun close() {
        jellyfin.close()
        Dispatchers.resetMain()
    }

    private fun reads(): Int {
        compose.waitUntil(WAIT_MILLIS) { jellyfin.hits("/Users/Me") > 0 }
        return jellyfin.hits("/Users/Me")
    }

    @Test
    fun `a user who signs in after a sign-out has their own configuration read, not the last user's`() {
        assertEquals(1, reads())
        state = UiState.Loading
        compose.waitForIdle()
        assertNull(playback)
        state = UiState.SignedIn(session = jellyfin.session().copy(userId = "user-2"))
        compose.waitForIdle()
        compose.waitUntil(WAIT_MILLIS) { jellyfin.hits("/Users/Me") > 1 }
        assertEquals(2, jellyfin.hits("/Users/Me"))
    }
}

private const val WAIT_MILLIS = 10_000L
