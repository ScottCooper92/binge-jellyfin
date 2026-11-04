package com.cooper.binge.companion.jellyfin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.FakeJellyfin
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

    fun <T : ViewModel> made(viewModel: T): T = viewModel.also { viewModels += it }

    fun close(jellyfin: FakeJellyfin) {
        val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        runBlocking { jobs.forEach { it.join() } }
        viewModels.clear()
        jellyfin.close()
        Dispatchers.resetMain()
    }
}
