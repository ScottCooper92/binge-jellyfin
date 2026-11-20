package com.cooper.homelab.ephyra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.server.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The one teardown every view model test shares. A view model's work is stopped first, then the fake
 * server closed, and only then is Main reset, so an event still in flight from the server's socket
 * thread has nothing left to land on when Main changes (#101). Cancellation must finish before
 * Main changes: cancelling alone leaves IO work and coroutine cleanup in flight (#358).
 *
 * Wrap each view model a test makes in [made]; call [close] from the test's `@After`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ViewModelTeardown {
    private val viewModels = mutableListOf<ViewModel>()
    private val connections = mutableListOf<Connection>()

    private companion object {
        /** How long a wait for a job to finish lasts before the looper is run again. */
        const val IDLE_POLL_MILLIS = 5L
    }

    fun <T : ViewModel> made(viewModel: T): T = viewModel.also { viewModels += it }

    /** A connection a test builds: closed with the view models, so its session watcher and saves end with the test. */
    fun made(connection: Connection): Connection = connection.also { connections += it }

    /**
     * [idle] runs the platform's main looper, for a test whose Main is [MainLooperDispatcher]: the cancelled work finishes on the main
     * thread, which this call blocks, so the looper is run in between checks. With Main as `Dispatchers.Unconfined` it is not needed.
     */
    fun close(
        jellyfin: FakeJellyfin,
        idle: () -> Unit = {},
    ) {
        val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        runBlocking {
            jobs.forEach { job ->
                while (!job.isCompleted) {
                    idle()
                    withTimeoutOrNull(IDLE_POLL_MILLIS) { job.join() }
                }
            }
        }
        viewModels.clear()
        connections.forEach { it.close() }
        connections.clear()
        jellyfin.close()
        Dispatchers.resetMain()
    }
}
