package com.cooper.binge.companion.jellyfin.ui.console

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.server.TUNER_TYPE_HDHOMERUN
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class TunerDiscoveryTest {
    @get:Rule val compose = createTakeDownAndroidComposeRule<ComponentActivity>()
    private val server = FakeJellyfin()
    private val sessions = testSessionStore()
    private val connection = Connection(OkHttpClient(), sessions, "Jellyfin Companion", "Test", "1.0", Dispatchers.IO)
    private val teardown = ViewModelTeardown()

    @Before fun before() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        runBlocking { sessions.save(server.session()) }
    }

    @After fun after() = teardown.close(server)

    private fun await(
        vm: LiveTvViewModel,
        check: (LiveTvUiState.Ready) -> Boolean,
    ): LiveTvUiState.Ready =
        runBlocking { withTimeout(10_000) { vm.state.first { it is LiveTvUiState.Ready && check(it) } } } as LiveTvUiState.Ready

    @Test fun `held discovery found none and failed keep actions and retry or address works`() {
        val vm = teardown.made(LiveTvViewModel(connection, withGuide = false))
        await(vm) { true }
        val held = server.hold("/LiveTv/Tuners/Discover")
        compose.setContent {
            val state by vm.state.collectAsState()
            JellyfinTheme {
                (state as? LiveTvUiState.Ready)?.sheet?.let {
                    TunerSheetContent(it, false, LiveTvActions(onDiscover = vm::discover, onForm = vm::form))
                }
            }
        }
        compose.runOnIdle { vm.discover() }
        held.awaitArrival()
        compose.onNodeWithText("Looking for tuners").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Add by address").assertIsDisplayed().assertIsNotEnabled()
        held.release()
        await(vm) { it.sheet is TunerSheet.Found }
        compose.onNodeWithText("HDHomeRun PRIME").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsEnabled()
        compose.onNodeWithText("Add by address").assertIsEnabled()

        server.discoveredTuners = "[]"
        compose.onNodeWithText("Retry").performClick()
        await(vm) { (it.sheet as? TunerSheet.Found)?.devices?.isEmpty() == true }
        compose.onNodeWithText("No new tuners").assertIsDisplayed()
        compose.onNodeWithText("Retry").assertIsEnabled()
        compose.onNodeWithText("Add by address").assertIsEnabled()

        val failure = server.hold("/LiveTv/Tuners/Discover")
        compose.onNodeWithText("Retry").performClick()
        failure.awaitArrival()
        failure.refuse(500, "Discovery unavailable")
        await(vm) { it.sheet is TunerSheet.Failed }
        compose.onNodeWithText("Retry").assertIsEnabled()
        compose.onNodeWithText("Add by address").assertIsEnabled().performClick()
        val form = await(vm) { it.sheet is TunerSheet.Form }.sheet as TunerSheet.Form
        assertEquals(TUNER_TYPE_HDHOMERUN, form.draft.type)
    }
}
