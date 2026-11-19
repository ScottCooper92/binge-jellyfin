package com.cooper.homelab.ephyra.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import com.binge.designsystem.component.LocalTabReselectSignal
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.homelab.ephyra.auth.JellyfinSession
import com.cooper.homelab.ephyra.server.JellyfinAccount
import com.cooper.homelab.ephyra.theme.JellyfinTheme
import com.cooper.homelab.ephyra.ui.nav.EphyraTab
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tap on the Server tab while it shows its root scrolls the console home back to the top. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class HomeScrollToTopOnReselectTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val signal = MutableSharedFlow<Any>(extraBufferCapacity = 1)

    private val state =
        UiState.SignedIn(
            JellyfinSession("http://192.168.1.2:8096", "Living room", "a1", "10.11.6", "u1", "sam", "not-a-token"),
            titles = null,
            health = ConnectionHealth.Healthy,
            account = JellyfinAccount("sam", Json.parseToJsonElement("""{"IsAdministrator":true}""").jsonObject),
        )

    private fun show(withSignal: Boolean = true) {
        compose.setContent {
            JellyfinTheme {
                CompositionLocalProvider(LocalTabReselectSignal provides signal.takeIf { withSignal }) {
                    JellyfinRoot(state, Actions())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun list(): SemanticsNodeInteraction = compose.onAllNodes(hasScrollAction()).onFirst()

    private fun scrolledBy(): Float = list().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollDown() {
        list().performScrollToIndex(3)
        compose.waitForIdle()
        assertTrue("the home should be scrolled down", scrolledBy() > 0f)
    }

    private fun reselect(tab: EphyraTab) {
        compose.runOnUiThread { signal.tryEmit(tab) }
        compose.waitForIdle()
    }

    @Test
    fun `reselecting the Server tab scrolls the home back to the top`() {
        show()
        scrollDown()
        reselect(EphyraTab.Server)
        assertEquals(0f, scrolledBy(), 0f)
    }

    @Test
    fun `another tab's reselect leaves the home where it is`() {
        show()
        scrollDown()
        val before = scrolledBy()
        reselect(EphyraTab.Home)
        assertEquals(before, scrolledBy(), 0f)
    }

    @Test
    fun `without a shell there is no signal and the home is left alone`() {
        show(withSignal = false)
        scrollDown()
        val before = scrolledBy()
        reselect(EphyraTab.Server)
        assertEquals(before, scrolledBy(), 0f)
    }
}
