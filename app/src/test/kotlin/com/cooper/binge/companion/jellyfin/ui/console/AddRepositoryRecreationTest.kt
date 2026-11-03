package com.cooper.binge.companion.jellyfin.ui.console

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.jellyfin.theme.JellyfinTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

private const val PORTRAIT = "w412dp-h900dp"
private const val LANDSCAPE = "w900dp-h412dp"
private const val SETTLE_MILLIS = 1_000L

/**
 * What is typed in the real Add repository sheet comes back when the Activity is recreated, whether the rotation turns the sheet
 * from a bottom sheet into a side sheet or back (binge-jellyfin#298, #301, #280). The design system keeps a task sheet's content
 * state itself (binge-design-system#1066), so the form's own `rememberSaveable`s need no key of their own.
 *
 * It drives an `ActivityController` and an empty compose rule, not a rule that owns the Activity: a rule's `setContent` wraps the
 * content and gives a recreated Activity different positional keys, which is how a window finds its saved state, so a sheet could
 * never come back under it (the design system's `BingeAdaptiveSheetRecreationTest` explains it). The empty rule only gives the test
 * its handle on the composition, to type into the field and read it. It builds nothing that needs taking down: the controller
 * is destroyed and the looper drained after each test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = PORTRAIT)
class AddRepositoryRecreationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var opened: ActivityController<ComponentActivity>? = null

    @After
    fun tearDown() {
        opened?.destroy()
        shadowOf(Looper.getMainLooper()).idle()
        opened = null
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SETTLE_MILLIS))
        compose.waitForIdle()
    }

    private fun show(controller: ActivityController<ComponentActivity>) {
        controller.get().setContent { JellyfinTheme { AddRepositorySheet(saving = false, actions = RepositoriesActions()) } }
        settle()
    }

    private fun recreate(qualifiers: String): ActivityController<ComponentActivity> {
        val controller = opened ?: error("nothing is open")
        RuntimeEnvironment.setQualifiers(qualifiers)
        controller.recreate()
        show(controller)
        return controller
    }

    private fun typeName(text: String) {
        compose.onNode(hasSetTextAction()).performTextInput(text)
        settle()
    }

    @Test
    fun `a name typed in a bottom sheet is still there after the rotation makes it a side sheet`() {
        opened = Robolectric.buildActivity(ComponentActivity::class.java).setup().also(::show)
        typeName("Mine")
        recreate(LANDSCAPE)
        compose.onNode(hasSetTextAction()).assertTextContains("Mine")
    }

    @Test
    fun `and from a side sheet back to a bottom sheet`() {
        RuntimeEnvironment.setQualifiers(LANDSCAPE)
        opened = Robolectric.buildActivity(ComponentActivity::class.java).setup().also(::show)
        typeName("Mine")
        recreate(PORTRAIT)
        compose.onNode(hasSetTextAction()).assertTextContains("Mine")
    }

    @Test
    fun `the step and the address typed in it survive too`() {
        opened = Robolectric.buildActivity(ComponentActivity::class.java).setup().also(::show)
        typeName("Mine")
        compose.onNodeWithText("Next").performClick()
        settle()
        typeName("https://repo.example/mine.json")
        recreate(LANDSCAPE)
        compose.onNode(hasSetTextAction()).assertTextContains("https://repo.example/mine.json")
    }
}
