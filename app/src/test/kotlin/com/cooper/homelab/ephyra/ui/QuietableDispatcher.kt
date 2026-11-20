package com.cooper.homelab.ephyra.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * `Dispatchers.IO` that can be made quiet: after [quiesce], nothing it was given runs, and nothing it was running is still running.
 *
 * A test that composes the real screens over real view models runs their work here. With Main as `Dispatchers.Unconfined`, a state
 * flow's collector in the composition resumes on the thread that emitted, so whatever runs here writes the composition's state, and
 * invalidates it, from an IO thread. The Compose test rule destroys the activity, and so disposes the composition, when the test body
 * returns, which is before the test's `@After`, so that work was still going and could be writing while the main thread disposed the
 * slot table (#898). Calling [quiesce] as the body ends leaves nothing running that could.
 */
internal class QuietableDispatcher(
    private val delegate: CoroutineDispatcher = Dispatchers.IO,
) : CoroutineDispatcher() {
    private companion object {
        const val QUIESCE_TIMEOUT_NANOS = 10_000_000_000L
    }

    private val lock = Any()
    private var quiet = false
    private val running = AtomicInteger()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        synchronized(lock) {
            // Work given once quiet is dropped: it was only ever going to write to a screen that is going away.
            if (quiet) return
            running.incrementAndGet()
        }
        try {
            delegate.dispatch(context) {
                try {
                    block.run()
                } finally {
                    running.decrementAndGet()
                }
            }
        } catch (e: RuntimeException) {
            running.decrementAndGet()
            throw e
        }
    }

    /** Stops taking work, and returns once what was already running has finished. */
    fun quiesce() {
        synchronized(lock) { quiet = true }
        val until = System.nanoTime() + QUIESCE_TIMEOUT_NANOS
        while (running.get() > 0) {
            if (System.nanoTime() > until) {
                val stacks =
                    Thread
                        .getAllStackTraces()
                        .entries
                        .filter { it.key.name.startsWith("DefaultDispatcher") }
                        .joinToString("\n") { (t, s) -> t.name + "\n  " + s.take(12).joinToString("\n  ") }
                error("IO work still running after $QUIESCE_TIMEOUT_NANOS ns: ${running.get()}\n$stacks")
            }
            Thread.onSpinWait()
        }
    }
}
