package com.cooper.binge.companion.jellyfin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTeardownTest {
    @Test
    fun `close waits for cancellation cleanup before resetting Main`() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val jellyfin = FakeJellyfin()
        val teardown = ViewModelTeardown()
        val viewModel = teardown.made(object : ViewModel() {})
        val cleaning = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        val executor = Executors.newSingleThreadExecutor()
        val job =
            viewModel.viewModelScope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleaning.complete(Unit)
                        finishCleanup.await()
                        // Cleanup can still dispatch to Main after IO work has been cancelled.
                        withContext(Dispatchers.Main) { }
                    }
                }
            }
        val closing = executor.submit { teardown.close(jellyfin) }
        try {
            runBlocking { withTimeout(5_000) { cleaning.await() } }
            assertThrows(TimeoutException::class.java) { closing.get(100, TimeUnit.MILLISECONDS) }
            finishCleanup.complete(Unit)
            closing.get(5, TimeUnit.SECONDS)
            assertTrue(job.isCompleted)
        } finally {
            finishCleanup.complete(Unit)
            try {
                closing.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
                jellyfin.close()
                Dispatchers.resetMain()
            }
        }
    }
}
