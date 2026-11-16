package com.cooper.binge.companion.jellyfin.tip

import android.app.Activity
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.review.ReviewPrompt
import com.cooper.homelab.core.review.ReviewSignalStore
import com.cooper.homelab.core.testing.FakeTipBilling
import com.cooper.homelab.core.testing.InMemoryDataStore
import com.cooper.homelab.core.tip.TIP_STEPS
import com.cooper.homelab.core.tip.TipBilling
import com.cooper.homelab.core.tip.TipOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L

private val PRICED = TIP_STEPS.associateWith { "£$it" }

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TipViewModelTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).get()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private val store = ReviewSignalStore(InMemoryDataStore())
    private val asked = mutableListOf<Any>()
    private val reviewPrompt =
        ReviewPrompt(
            store,
            { asked += it },
            { true },
            TestScope(UnconfinedTestDispatcher()),
            Clock { Instant.fromEpochMilliseconds(NOW) },
        )

    private fun viewModel(billing: TipBilling) = TipViewModel(billing, reviewPrompt)

    /** A user who has earned the prompt, so only the tip can be what stops it. */
    private suspend fun earn() {
        store.stampFirstLaunchIfUnset(NOW - 20.days.inWholeMilliseconds)
        repeat(5) { store.incrementPositiveActionCount() }
    }

    /** Collects [viewModel]'s events for the rest of the test. */
    private fun TestScope.eventsOf(viewModel: TipViewModel): List<TipEvent> {
        val seen = mutableListOf<TipEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { seen += it } }
        return seen
    }

    @Test
    fun `the jar is ready once every step is priced`() {
        assertEquals(TipUiState.Ready(PRICED), viewModel(FakeTipBilling(PRICED)).uiState.value)
    }

    @Test
    fun `the jar is unavailable where Play cannot price it`() {
        assertEquals(TipUiState.Unavailable, viewModel(FakeTipBilling(null)).uiState.value)
    }

    @Test
    fun `a bought tip says thanks`() =
        runTest {
            val billing = FakeTipBilling(PRICED)
            val viewModel = viewModel(billing)
            val events = eventsOf(viewModel)
            viewModel.give(activity, 5)
            assertEquals(listOf<TipEvent>(TipEvent.Thanks), events)
            assertEquals(listOf(5), billing.given)
        }

    @Test
    fun `a tip waiting on Play and a failed one each say so`() =
        runTest {
            val pending = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Pending)))
            val first = eventsOf(pending)
            pending.give(activity, 3)
            assertEquals(listOf<TipEvent>(TipEvent.Pending), first)

            val failed = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Failed)))
            val second = eventsOf(failed)
            failed.give(activity, 3)
            assertEquals(listOf<TipEvent>(TipEvent.Failed), second)
        }

    @Test
    fun `a cancelled tip says nothing and can be tried again`() =
        runTest {
            val billing = FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Cancelled))
            val viewModel = viewModel(billing)
            val events = eventsOf(viewModel)
            viewModel.give(activity, 5)
            assertTrue(events.isEmpty())
            billing.outcome = CompletableDeferred(TipOutcome.Thanks)
            viewModel.give(activity, 5)
            assertEquals(listOf<TipEvent>(TipEvent.Thanks), events)
            assertEquals(listOf(5, 5), billing.given)
        }

    @Test
    fun `a second tap while a purchase is under way does not start another`() =
        runTest {
            val billing = FakeTipBilling(PRICED, CompletableDeferred())
            val viewModel = viewModel(billing)
            viewModel.give(activity, 5)
            viewModel.give(activity, 10)
            assertEquals(listOf(5), billing.given)
            billing.outcome.complete(TipOutcome.Thanks)
        }

    @Test
    fun `nothing is given while the jar is unavailable`() {
        val billing = FakeTipBilling(null)
        viewModel(billing).give(activity, 5)
        assertTrue(billing.given.isEmpty())
    }

    @Test
    fun `a given tip ends the review prompt for the session`() =
        runTest {
            earn()
            val viewModel = viewModel(FakeTipBilling(PRICED))
            val events = eventsOf(viewModel)
            viewModel.give(activity, 5)
            assertEquals(listOf<TipEvent>(TipEvent.Thanks), events)
            reviewPrompt.maybeAsk(activity)
            assertEquals(emptyList<Any>(), asked)
        }

    @Test
    fun `a tip still pending also ends it`() =
        runTest {
            earn()
            val viewModel = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Pending)))
            val events = eventsOf(viewModel)
            viewModel.give(activity, 5)
            assertEquals(listOf<TipEvent>(TipEvent.Pending), events)
            reviewPrompt.maybeAsk(activity)
            assertEquals(emptyList<Any>(), asked)
        }

    @Test
    fun `a cancelled or failed tip leaves the prompt to be asked`() =
        runTest {
            earn()
            viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Cancelled))).give(activity, 5)
            val failed = viewModel(FakeTipBilling(PRICED, CompletableDeferred(TipOutcome.Failed)))
            val events = eventsOf(failed)
            failed.give(activity, 5)
            assertEquals(listOf<TipEvent>(TipEvent.Failed), events)
            reviewPrompt.maybeAsk(activity)
            assertEquals(listOf<Any>(activity), asked)
        }
}
