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

    fun <T : ViewModel> made(viewModel: T): T = viewModel.also { viewModels += it }

    /** A connection a test builds: closed with the view models, so its session watcher and saves end with the test. */
    fun made(connection: Connection): Connection = connection.also { connections += it }

    /**
     * Stops every view model's work and waits for it to finish, and closes nothing else. For a test that has to have its work quiet
     * before the test ends, such as one whose Compose rule destroys the activity when the body returns, ahead of [close]; [close]
     * then finds nothing left to wait for.
     */
    fun stop() {
        val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        runBlocking { jobs.forEach { it.join() } }
    }

    fun close(jellyfin: FakeJellyfin) {
        stop()
        viewModels.clear()
        connections.forEach { it.close() }
        connections.clear()
        jellyfin.close()
        Dispatchers.resetMain()
    }
}
