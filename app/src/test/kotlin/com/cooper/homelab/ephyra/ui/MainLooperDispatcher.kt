package com.cooper.homelab.ephyra.ui

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * Main as it is on a device: work runs on the platform's main thread and nowhere else, and `Main.immediate` runs inline only when the
 * caller is already on it. A test that composes the real screens over real view models sets this as Main (`Dispatchers.setMain`), so a
 * state flow's collector in the composition resumes on the main thread whichever thread emits. With `Dispatchers.Unconfined` as Main it
 * resumes on the emitting thread, so the composition's state was written, and invalidated, from IO and OkHttp threads while the main
 * thread composed and disposed it (#898).
 *
 * Work posted here runs when the main looper runs. On a device it always does; in a Robolectric test it runs when something idles the
 * looper, which a Compose test does in `waitForIdle` and when it evaluates a node, and nothing else does. So with this as Main:
 * - a wait that does not touch Compose, such as one on a server's requests, must call `waitForIdle()` in its condition;
 * - never block the main thread on work that runs on it (`runBlocking { job.join() }`, `Flow.first()` in `runBlocking`): wait with
 *   `waitUntil` instead, or run the looper while waiting, as [ViewModelTeardown.close] does with its `idle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal object MainLooperDispatcher : MainCoroutineDispatcher() {
    override val immediate: MainCoroutineDispatcher = Immediate

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        Handler(Looper.getMainLooper()).post(block)
    }

    private object Immediate : MainCoroutineDispatcher() {
        override val immediate: MainCoroutineDispatcher get() = this

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = Looper.myLooper() != Looper.getMainLooper()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            Handler(Looper.getMainLooper()).post(block)
        }
    }
}
