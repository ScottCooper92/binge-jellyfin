package com.cooper.binge.companion.jellyfin.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.cooper.binge.companion.jellyfin.FakeJellyfin
import com.cooper.binge.companion.jellyfin.InMemoryDataStore
import com.cooper.binge.companion.jellyfin.server.Connection
import com.cooper.binge.companion.jellyfin.session
import com.cooper.binge.companion.jellyfin.testSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The worker WorkManager runs, end to end against the fake server: its result follows the poll's outcome. */
@RunWith(RobolectricTestRunner::class)
class NotificationWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jellyfin = FakeJellyfin().apply { administrator = true }
    private val sessions = testSessionStore()
    private val prefs = NotificationPrefs(InMemoryDataStore())
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private val connection = Connection(OkHttpClient(), sessions, "Ephyra", "Test", "1.0", Dispatchers.IO)
    private val runner =
        PollRunner(
            ActivityChecker(connection, prefs, notifier),
            PollReactor(notifier, scheduler, prefs),
        )

    @After
    fun close() {
        connection.close()
        jellyfin.close()
    }

    private fun run(): ListenableWorker.Result =
        runBlocking {
            TestListenableWorkerBuilder<NotificationWorker>(context)
                .setWorkerFactory(NotificationWorkerFactory { runner })
                .build()
                .doWork()
        }

    @Test
    fun `a successful check succeeds and tells the user what it found`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.activityEntries = listOf(activityJson(1, "SessionStarted"))
        assertEquals(ListenableWorker.Result.success(), run())
        jellyfin.activityEntries = listOf(activityJson(2, "AuthenticationFailed"), activityJson(1, "SessionStarted"))
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(listOf(ActivitySummary(failedSignIns = 1)), notifier.summaries)
    }

    @Test
    fun `a server that cannot be reached is retried`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.status["/Users/Me"] = 500
        assertEquals(ListenableWorker.Result.retry(), run())
    }

    @Test
    fun `a refused sign-in is not retried`() {
        runBlocking { sessions.save(jellyfin.session()) }
        jellyfin.status["/Users/Me"] = 401
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(1, notifier.connectionProblems)
    }
}
