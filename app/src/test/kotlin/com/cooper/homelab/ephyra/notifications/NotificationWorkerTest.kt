package com.cooper.homelab.ephyra.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.cooper.homelab.core.testing.InMemoryDataStore
import com.cooper.homelab.ephyra.FakeJellyfin
import com.cooper.homelab.ephyra.auth.ServerIdentity
import com.cooper.homelab.ephyra.server.Connection
import com.cooper.homelab.ephyra.server.DiscardLogger
import com.cooper.homelab.ephyra.session
import com.cooper.homelab.ephyra.testSessionStore
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
    private val serverIdentity = ServerIdentity(InMemoryDataStore())
    private val notifier = FakeNotifier()
    private val scheduler = FakeScheduler()
    private val connection = Connection(OkHttpClient(), sessions, "Ephyra", "Test", "1.0", Dispatchers.IO, DiscardLogger)
    private val runner =
        PollRunner(
            ActivityChecker(connection, testFeedCursors(), LegacyActivityCursor(prefs), serverIdentity, notifier, DiscardLogger),
            pollReactor(notifier, scheduler, prefs) { false },
            serverIdentity,
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
