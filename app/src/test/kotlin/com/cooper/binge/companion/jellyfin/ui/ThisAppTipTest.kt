package com.cooper.binge.companion.jellyfin.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.testing.createTakeDownAndroidComposeRule
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import com.cooper.binge.companion.jellyfin.tip.TIP_STEPS
import com.cooper.binge.companion.jellyfin.tip.TipEvent
import com.cooper.binge.companion.jellyfin.tip.TipUiState
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThisAppTipTest {
    @get:Rule
    val compose = createTakeDownAndroidComposeRule<ComponentActivity>()

    private val priced = TipUiState.Ready(TIP_STEPS.associateWith { "£$it" })

    @Test
    fun `there is no tip row until every step is priced`() {
        compose.setContent { JellyfinTheme { ThisAppScreen(Actions(), onBack = {}) } }
        compose.onAllNodesWithText("Buy me a coffee").assertCountEquals(0)
    }

    @Test
    fun `a priced jar opens its sheet at 5 and gives the step it lands on`() {
        val given = mutableListOf<Int>()
        compose.setContent { JellyfinTheme { ThisAppScreen(Actions(), onBack = {}, tip = priced, onGiveTip = { given += it }) } }
        compose.onNodeWithText("Buy me a coffee").performClick()
        compose.onNodeWithText("Give £5").performClick()
        assertEquals(listOf(5), given)
    }

    @Test
    fun `a failed tip explains itself in a dialog`() {
        compose.setContent {
            JellyfinTheme { ThisAppScreen(Actions(), onBack = {}, tip = priced, tipEvents = flowOf(TipEvent.Failed)) }
        }
        compose.onNodeWithText("The tip didn't go through").assertExists()
    }
}
